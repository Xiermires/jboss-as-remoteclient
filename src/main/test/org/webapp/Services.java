package org.webapp;

import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

public class Services
{
    private static InitialContext ic = null;

    Services()
    {
    }

    private static boolean forceRemote = false;

    public static boolean setForceRemote(boolean forceRemote)
    {
        boolean old = forceRemote;
        Services.forceRemote = forceRemote;
        return old;
    }

    static final Map<Class<?>, Object> cache = new HashMap<Class<?>, Object>();

    static void init()
    {
        final Map<Class<?>, String> nameBindings = new HashMap<>();
        if (forceRemote)
            nameBindings.putAll(getFromBeansService());
        else
        {
            try
            {
                nameBindings.putAll(getFromInitialContext());
            }
            catch (Exception e)
            {
                // fallback to beans service
                nameBindings.putAll(getFromBeansService());
            }
        }
        for (Entry<Class<?>, String> entry : nameBindings.entrySet())
            cache.put(entry.getKey(), lookupWrapEx(entry.getValue()));
    }

    static
    {
        init();
    }
    
    private static Map<Class<?>, String> getFromInitialContext() throws NamingException, ClassNotFoundException
    {
        final Map<Class<?>, String> nameBindings = new HashMap<>();
        final Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
        props.put(Context.PROVIDER_URL, "remote://localhost:4447");
        props.put("jboss.naming.client.ejb.context", "true");
        props.put("org.jboss.ejb.client.scoped.context", "true");

        ic = new InitialContext(props);

        nameBindings.putAll(getNameBindings(""));
        return nameBindings;
    }

    private static Map<Class<?>, String> getFromBeansService()
    {
        final Map<Class<?>, String> nameBindings = new HashMap<>();
        // fallback to Beans.
        final Properties props = new Properties();
        props.put("remote.connectionprovider.create.options.org.xnio.Options.SSL_ENABLED", "false");
        props.put("remote.connections", "default");
        props.put("remote.connection.default.port", "4447");
        props.put("remote.connection.default.host", "127.0.0.1");
        props.put("remote.connection.default.username", "jboss");
        props.put("remote.connection.default.password", "JBOSSPASS1!");
        props.put("remote.connection.default.connect.options.org.xnio.Options.SASL_POLICY_NOANONYMOUS", "false");
        props.put("remote.connection.default.connect.options.org.xnio.Options.SASL_POLICY_NOPLAINTEXT", "false");
        props.put("remote.connection.default.connect.options.org.xnio.Options.SASL_DISALLOWED_MECHANISMS", "JBOSS-LOCAL-USER");
        props.put(Context.URL_PKG_PREFIXES, "org.jboss.ejb.client.naming");
        props.put("jboss.naming.client.ejb.context", "true");
        props.put("org.jboss.ejb.client.scoped.context", "true");

        try
        {
            ic = new InitialContext(props);

            final String moduleName = "jboss-as-remoteclient-0.0.1-SNAPSHOT";
            final String beanName = Beans.class.getSimpleName();
            final String viewClassName = BeansRemote.class.getName();
            final BeansRemote beans = (BeansRemote) ic.lookup("ejb:" + "/" + moduleName + "/" + beanName + "!" + viewClassName);

            nameBindings.putAll(beans.getBeans());
        }
        catch (NamingException | ClassNotFoundException e1)
        {
            throw new RuntimeException("Beans load failed. Blow up.");
        }
        return nameBindings;
    }

    private static Object lookupWrapEx(String f)
    {
        try
        {
            return ic.lookup(f);
        }
        catch (NamingException e)
        {
            throw new RuntimeException("Just read bean not available", e);
        }
    }

    @SuppressWarnings("unchecked")
    // should be safe
    public static <T> T find(Class<T> interfaze)
    {
        return (T) cache.get(interfaze);
    }

    private static Map<Class<?>, String> getNameBindings(String namespace) throws NamingException, ClassNotFoundException
    {
        final Map<Class<?>, String> m = new HashMap<Class<?>, String>();
        final NamingEnumeration<Binding> bindings = ic.listBindings(namespace);
        while (bindings.hasMore())
        {
            try
            {
                final Binding next = bindings.next();
                final String nextName = next.getName();
                if (next.getObject() instanceof Context)
                    m.putAll(getNameBindings(namespace + "/" + nextName + "/"));
                else
                {
                    m.put(Class.forName(parseInterfaceName(nextName)), namespace + nextName);
                }
            }
            catch (Exception e)
            {
                // TODO log class exceptions (service unavailable for the current client).
            }
        }
        return m;
    }

    private static String parseInterfaceName(String name)
    {
        return name.substring(name.indexOf("!") + 1, name.length());
    }
}
