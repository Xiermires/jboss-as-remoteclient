package org.webapp;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.jboss.ejb.client.ContextSelector;
import org.jboss.ejb.client.EJBClientConfiguration;
import org.jboss.ejb.client.EJBClientContext;
import org.jboss.ejb.client.PropertiesBasedEJBClientConfiguration;
import org.jboss.ejb.client.remoting.ConfigBasedEJBClientContextSelector;
import org.junit.Test;

public class Client
{
    @Test
    public void test1() throws NamingException
    {
        final Properties props = new Properties();
        props.put("remote.connectionprovider.create.options.org.xnio.Options.SSL_ENABLED", "false");
        props.put("remote.connections", "default");
        props.put("remote.connection.default.port", "4447");
        props.put("remote.connection.default.host", "127.0.0.1");
        props.put("remote.connection.default.username", "testuser");
        props.put("remote.connection.default.password", "testuser!");
        props.put("remote.connection.default.connect.options.org.xnio.Options.SASL_POLICY_NOANONYMOUS", "false");

        final EJBClientConfiguration ejbClientConfiguration = new PropertiesBasedEJBClientConfiguration(props);
        final ContextSelector<EJBClientContext> contextSelector = new ConfigBasedEJBClientContextSelector(ejbClientConfiguration);
        EJBClientContext.setSelector(contextSelector);

        final Properties properties = new Properties();
        properties.put(Context.URL_PKG_PREFIXES, "org.jboss.ejb.client.naming");

        final InitialContext ic = new InitialContext(properties);

        final String moduleName = "webapp-0.0.1-SNAPSHOT";
        final String beanName = Math.class.getSimpleName();
        final String viewClassName = MathRemote.class.getName();
        final MathRemote mr = (MathRemote) ic.lookup("ejb:" + "/" + moduleName + "/" + beanName + "!" + viewClassName);
        assertThat(2.0, is(mr.sum(1.0, 1.0)));
    }

    @Test
    public void test2() throws NamingException
    {
        final Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
        props.put(Context.PROVIDER_URL, "remote://localhost:4447");
        props.put("remote.connection.default.username", "testuser");
        props.put("remote.connection.default.password", "testuser!");
        props.put("jboss.naming.client.ejb.context", "true");

        final InitialContext ic = new InitialContext(props);

        final MathRemote mr = (MathRemote) ic.lookup("/webapp-0.0.1-SNAPSHOT/Math!org.webapp.MathRemote");
        assertThat(2.0, is(mr.sum(1.0, 1.0)));
    }

    @Test
    public void test3()
    {
        final MathRemote mr = Services.find(MathRemote.class);
        assertThat(2.0, is(mr.sum(1.0, 1.0)));
    }
}
