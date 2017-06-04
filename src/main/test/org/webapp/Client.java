package org.webapp;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;

import org.junit.Test;

public class Client
{
    @Test
    public void test1() throws Exception
    {
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

        final InitialContext ic = new InitialContext(props);

        final String moduleName = "jboss-as-remoteclient-0.0.1-SNAPSHOT";
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
        props.put("jboss.naming.client.ejb.context", "true");
        props.put("org.jboss.ejb.client.scoped.context", "true");

        final InitialContext ic = new InitialContext(props);

        final MathRemote mr = (MathRemote) ic.lookup("/jboss-as-remoteclient-0.0.1-SNAPSHOT/Math!org.webapp.MathRemote");
        assertThat(2.0, is(mr.sum(1.0, 1.0)));
    }

    @Test
    public void test3()
    {
        Services.init();
        final MathRemote mr = Services.find(MathRemote.class);
        assertThat(2.0, is(mr.sum(1.0, 1.0)));
    }

    @Test
    public void test4()
    {
        Services.cache.clear();
        Services.setForceRemote(true); // JBoss runs in localhost and INITIAL_CONTEXT_FACTORY initial
                                     // context ignores the JBOSS-LOCAL-USER clause.
                                     // This forces to use the Beans remote bean.
        Services.init();
        final MathRemote mr = Services.find(MathRemote.class);
        assertThat(2.0, is(mr.sum(1.0, 1.0)));
    }
}
