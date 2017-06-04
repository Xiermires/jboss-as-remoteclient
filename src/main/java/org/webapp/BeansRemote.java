package org.webapp;

import java.util.Map;

import javax.naming.NamingException;

public interface BeansRemote
{
    /**
     * Returns an interface list plus the name bindings in the JNDI using the ejb: namespace.
     * <p>
     * For instance:
     * 
     * Entry<> = { ISomeBeanRemoteInterface.class, "ejb:earName/moduleName//Bean!ISomeBeanRemoteInterface"
     */
    Map<Class<?>, String> getBeans() throws ClassNotFoundException, NamingException;
}
