# jboss-as-remoteclient
Remote calls to JBoss AS 7.x

This app can be built with maven using goal : 'compile war:war', then copy the war into the JBoss 7.x deployments folder.

Small description
-----------------

Prior to JBoss 7.x I used to include a ServiceLocator in clients, so that they would use it to obtain remote ejb proxys to work with.

That was pretty straight forward because the jnp InitialContext supported the #list and #listBindings methods. So, it was effortlessly: iterate, cache, provide later through a "<T> T find(Class<T> interfaze)" method.

JBoss 7.x drop jnp and handled the JNDI with the remoting project, plus added some new namespaces which in theory improve the performance and add extra granularity to clients. 

Problem. The EJBRemoteContext doesn't support list or listBinding. So, the mentioned ServiceLocator can no longer iterate the bound services. 

Identifying the different scenarios
-----------------------------------

Identifying most of the different scenarios has taken a while. I don't know if because at that point the technology wasn't mature, but it looks extrem overcomplicated and error-prone.

First let's mention that it is still possible to obtain remotelly an InitialContext that supports #list and #listBindings. Let's see how:

```java
    final Properties props = new Properties();
    props.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
    props.put(Context.PROVIDER_URL, "remote://localhost:4447");
    props.put("jboss.naming.client.ejb.context", "true");
    props.put("org.jboss.ejb.client.scoped.context", "true");

    final InitialContext ic = new InitialContext(props);
```

This code has unfortunately one pitfall. 

- It only works if the JBoss server is deployed as localhost in the same machine as the ejb client. 

Notice that no auth is being used. That is intended, even when JBoss is indeed configured to use ApplicationRealm for remote connections, the InitialContext identifies there is a JBoss in localhost and apply some accessing optimizations. Between them, ignoring the auth.

One could think, no problem, JBoss 7.x provides new options that allow turning off those optimizations. So, adding these options:

"remote.connections=1"
"remote.connection.1.connect.options.org.xnio.Options.SASL_DISALLOWED_MECHANISMS=JBOSS-LOCAL-USER"

shall turn them off. Unfortunately, no matter how you add the option (jboss-ejb-client.properties, programatically through the selectors, directly in the Properties from 7.2 onwards) it won't work.

The remote InitialContextFactory ignores any provided ejb-client properties. 

As mentioned, the above method won't work if JBoss is deployed in a remote host. The InitialContext fails to authentificate (obviously after adding the CREDENTIALS, password in base64 and so on). 

Why is that ? No clue. Only reason that comes to mind is that, since the InitialContext is ignoring ejb-client properties, you need a fully-set up SSL, etc. 


<b>Using the JBoss recommended approach</b>

JBoss recommends using a new approach to invoke the remote clients. Since we are using EAP 6.1 for this tests (equivalent to JBoss AS 7.2.Final), we can ignore the jboss-ejb-client.properties file or its programatically version and add the properties direct in the properties file. The JBoss client will set up the EJBClientContext for us (nice).

So, with this code we get an InitialContext ready to be used as JBoss 7.x recommends us to.

```java
    final Properties props = new Properties();
    // ejb-client.properties
    props.put("remote.connectionprovider.create.options.org.xnio.Options.SSL_ENABLED", "false");
    props.put("remote.connections", "default");
    props.put("remote.connection.default.port", "4447");
    props.put("remote.connection.default.host", "127.0.0.1");
    props.put("remote.connection.default.username", "jboss");
    props.put("remote.connection.default.password", "JBOSSPASS1!");
    props.put("remote.connection.default.connect.options.org.xnio.Options.SASL_POLICY_NOANONYMOUS", "false"); // allow anonymous logins (this is useless since the remoting is configured as ApplicationRealm)
    props.put("remote.connection.default.connect.options.org.xnio.Options.SASL_POLICY_NOPLAINTEXT", "false"); // allows plain pw, no base64
    props.put("remote.connection.default.connect.options.org.xnio.Options.SASL_DISALLOWED_MECHANISMS", "JBOSS-LOCAL-USER"); // always remote
    
    // initial context.
    props.put(Context.URL_PKG_PREFIXES, "org.jboss.ejb.client.naming");
    props.put("jboss.naming.client.ejb.context", "true");
    props.put("org.jboss.ejb.client.scoped.context", "true");
   
    final InitialContext ic = new InitialContext(props);
```

This InitialContext works both when JBoss is deployed in localhost or in some remote host but it has the following pitfalls. 

1) This InitialContext doesn't support #list or #listBindings. So, auto-discovery of bound methods is not available. By using this method, you are kind of "obliged" to let your clients know how you package your application (earName, moduleName).
   Problem is that needs maintenance. When we create a new module, bean or whatsoever, we must communicate the client that a new bean is available... manually, so that it can build the "ejb:ear/module/.../" name. 
   
2) The InitialContext doesn't fail while looking up for non bound beans. That is a feature, the new system doesn't check the server while building the proxy. So it can build proxys that point nowhere. This proxys will fall when invoking
   their methods (runtime), and not during the setting up phase. For instance check this code out:

   ```java
    
    // set up properties as above
    final InitialContext ic = new InitialContext(properties);

    final String moduleName = "jboss-as-remoteclient-0.0.1-SNAPSHOT";
    final String beanName = Math.class.getSimpleName() + "thisSuffixDoesntExist"; // not bound name
    final String viewClassName = MathRemote.class.getName();
    final MathRemote mr = (MathRemote) ic.lookup("ejb:" + "/" + moduleName + "/" + beanName + "!" + viewClassName); // works
    assertThat(2.0, is(mr.sum(1.0, 1.0))); // fails 
    
    ```

While the second pitfall is annoying, it is the first one that prevents creating a ServiceLocator that supports our current beans and all future additions without maintenance. Fortunately we can workaround it.

The idea is to create a single remote method to auto discover the bound services, since we know that once inside JBoss we can call new InitialContext().list... without problems.

So, we create the following remote bean.

```java
public interface BeansRemote
{
    /**
     * Returns a map of the bound beans interfaces plus its jndi name.
     * <p>
     * For instance:
     * 
     * Entry<> = { ISomeBeanRemoteInterface.class, "ejb:earName/moduleName//Bean!ISomeBeanRemoteInterface"
     */
    Map<Class<?>, String> getBeans() throws ClassNotFoundException, NamingException;
}
```

Now we can create a ServiceLocator that access remotelly the BeansRemote#getBeans() using the JBoss recommended approach, get the list of { bean interface : jndi name } bound and use it to access whenever the client requests for an interface.

This also has the advantadge that it workarounds the second pitfall, since client cannot mess up while building the jndi name. 

Summary
-------

We kind of proposed how to build a ServiceLocator for JBoss 7.x remote clients that works in any scenario (localhost, remotehost, auth, no auth). 

Example of use:

```java
    final MathRemote mr = Services.find(MathRemote.class);
    assertThat(2.0, is(mr.sum(1.0, 1.0)));
```