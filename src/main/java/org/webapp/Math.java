package org.webapp;

import javax.ejb.Remote;
import javax.ejb.Stateless;

@Stateless
@Remote(MathRemote.class)
public class Math implements MathRemote
{
    @Override
    public double sum(double a, double b)
    {
        return a + b;
    }
}
