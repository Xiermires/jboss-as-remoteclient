package org.webapp;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

public class Services
{
    private Services()
    {
    }

    private static final Map<Class<?>, Object> cache = new HashMap<Class<?>, Object>();

    static
    {
        try
        {
            final Properties props = new Properties();
            props.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
            props.put(Context.PROVIDER_URL, "remote://localhost:4447");
            props.put("remote.connection.default.username", "testuser");
            props.put("remote.connection.default.password", "testuser!");
            props.put("jboss.naming.client.ejb.context", "true");

            cache.putAll(getProxies(new InitialContext(props), ""));
        }
        catch (Exception e)
        {
            throw new RuntimeException("Initialization failed. Blow up.");
        }
    }
    
    @SuppressWarnings("unchecked") // should be safe
    public static <T> T find(Class<T> interfaze)
    {
        return (T) cache.get(interfaze);
    }

    // Support default bindings in JBoss AS 7.2 and forward. WildFly untested. I guess it's the
    // same.
    private static Map<Class<?>, Object> getProxies(InitialContext ic, String namespace) throws NamingException,
            ClassNotFoundException
    {
        final Map<Class<?>, Object> m = new HashMap<Class<?>, Object>();
        final NamingEnumeration<Binding> bindings = ic.listBindings(namespace);
        while (bindings.hasMore())
        {
            final Binding next = bindings.next();
            if (next.getObject() instanceof Context)
                m.putAll(getProxies(ic, namespace + "/" + next.getName() + "/"));
            else m.put(Class.forName(parseInterfaceName(next.getName())), next.getObject());
        }
        return m;
    }

    private static String parseInterfaceName(String name)
    {
        return name.substring(name.indexOf("!") + 1, name.length());
    }
}
