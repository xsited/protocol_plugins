package org.pcmm.test;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.pcmm.gates.IPCError;
import org.pcmm.gates.impl.PCError;

public class PCErrorTest {

    IPCError error;

    @Before
    public void init() {
        error = new PCError();
        error.setErrorCode((short) 1);
    }

    @Test
    public void testGetDescription() {

/*
        for (IPCError.Description d : IPCError.Description.values()) {
            error.setErrorCode(d.getCode());
            Assert.assertNotNull(error.getDescription());
            System.out.println(error.getDescription());
        }
*/

    }

}
