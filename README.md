# jboss-as-remoteclient
Remote calls to JBoss AS 7.x

It took some hours but I finally manage to make auto discovery of the jboss bindings in JBoss AS 7.2. 

This app can be built with maven using goal : 'compile war:war', then copy the war into the JBoss deployments folder.

```java

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

```

I had some similar setup in JBoss 4/5 w/o issues and I was having trouble into using the same concept in JBoss 7.x.

The advantadge is that naming convention is not needed. Since once the proxyCache is built, you can get your process instances by means of the iterface. 

For instance a client would do:

``` 

    final MathRemote mathRemote = Services.find(MathRemote.class);
    mathRemote.sum(1.0, 1.0);
    
```
   
This approach doesn't work previous to 7.2. There are bugs in 7.1.x versions and InitialContext#list() / #listBindings() don't list any remotes.

Apparently the JBoss 7.x recommended approach is better cause it also considers load balancing. In this case, the Services class could
create a Map<Class<?>, String>> where the class is still the interface and the String is the "ejb:/application/module/..." jndi like id. 
Then create an initial context using the recommended approach (see below) and do the lookups of the strings stored in the cache. Still autodiscovery, plus 
any advantages the Context.URL_PKG_PREFIXES approach might bring.


----------------------------------------------------------------------------------------------------------------------------------------------------


I add also how to do remote calls to ejbs in JBoss 7.1.x or later if willing to stick to the naming conventions.

Calling using JBoss AS 7.x recommended way:

```

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
    
```

This approach has two pitfalls. 

1) Since it "optimize" the lookup by not calling the server. If the given name is wrong it won't complain, but it will fail when trying to use it.

``` 
    
    // set up properties as above
    final InitialContext ic = new InitialContext(properties);

    final String moduleName = "webapp-0.0.1-SNAPSHOT";
    final String beanName = Math.class.getSimpleName() + "thisNameIsWrong"; // not bound name
    final String viewClassName = MathRemote.class.getName();
    final MathRemote mr = (MathRemote) ic.lookup("ejb:" + "/" + moduleName + "/" + beanName + "!" + viewClassName); // works
    assertThat(2.0, is(mr.sum(1.0, 1.0))); // fails 
    
```

Running this code will throw the following exception:

java.lang.IllegalStateException: EJBCLIENT000025: No EJB receiver available for handling 
[appName:, moduleName:webapp-0.0.1-SNAPSHOT, distinctName:]...

2) When calling either ic.list(""), or ic.listBindings("") it throws the following exception:

javax.naming.NoInitialContextException: Need to specify class name in environment or system property, 
or as an applet parameter, or in an application resource file:  java.naming.factory.initial

To solve that the remote properties are required:

```

    props.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
    props.put(Context.PROVIDER_URL, "remote://localhost:4447");
    
```


Calling using the old remote way: 

```

    final Properties props = new Properties();
    props.put(Context.INITIAL_CONTEXT_FACTORY, "org.jboss.naming.remote.client.InitialContextFactory");
    props.put(Context.PROVIDER_URL, "remote://localhost:4447");
    props.put("remote.connection.default.username", "testuser");
    props.put("remote.connection.default.password", "testuser!");
    props.put("jboss.naming.client.ejb.context", "true");

    final InitialContext ic = new InitialContext(props);

    final MathRemote mr = (MathRemote) ic.lookup("/webapp-0.0.1-SNAPSHOT/Math!org.webapp.MathRemote");
    assertThat(2.0, is(mr.sum(1.0, 1.0)));
    
```

Previous pitfalls don't affect this way. Both list/listBinding work. Plus in the case a naming is wrong, lookup throws a name not bound exception like.

The drawback is that it is less performant.