/*******************************************************************************
 * Copyright (c) 2017, Xavier Miret Andres <xavier.mires@gmail.com>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 *******************************************************************************/
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
