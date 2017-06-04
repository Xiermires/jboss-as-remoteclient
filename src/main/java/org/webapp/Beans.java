package org.webapp;

import java.util.HashMap;
import java.util.Map;

import javax.ejb.Remote;
import javax.ejb.Stateless;
import javax.naming.Binding;
import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

@Stateless
@Remote(BeansRemote.class)
public class Beans implements BeansRemote
{

    @Override
    public Map<Class<?>, String> getBeans() throws ClassNotFoundException, NamingException
    {
        return getBeans(new InitialContext(), "java:jboss/exported");
    }

    private static Map<Class<?>, String> getBeans(InitialContext ic, String namespace) throws NamingException,
            ClassNotFoundException
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
                    m.putAll(getBeans(ic, namespace + "/" + nextName + "/"));
                else
                {
                    m.put(Class.forName(parseInterfaceName(nextName)), namespace.replace("java:jboss/exported", "ejb:") + "/" + nextName);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }
        return m;
    }

    private static String parseInterfaceName(String name)
    {
        return name.substring(name.indexOf("!") + 1, name.length());
    }
}
