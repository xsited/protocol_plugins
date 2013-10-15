/**
 *
 */
package org.pcmm.test;

import org.pcmm.rcd.ICMTS;
import org.pcmm.rcd.IPCMMPolicyServer;
import org.pcmm.rcd.impl.CMTS;
import org.pcmm.rcd.impl.PCMMPolicyServer;

/**
 *
 */
public class Main {

    /**
     * @param args
     */
    public static void main(String[] args) {
        ICMTS icmts = new CMTS();
        icmts.startServer();
        IPCMMPolicyServer ps = new PCMMPolicyServer();
        ps.requestCMTSConnection("localhost");
        ps.gateSet();

    }
}
