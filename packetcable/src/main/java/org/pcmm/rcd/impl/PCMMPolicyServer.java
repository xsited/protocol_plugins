/**
 @header@
 */
package org.pcmm.rcd.impl;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Properties;

import org.pcmm.gates.IAMID;
import org.pcmm.gates.IClassifier;
import org.pcmm.gates.IExtendedClassifier;
import org.pcmm.gates.IGateSpec;
import org.pcmm.gates.IGateSpec.DSCPTOS;
import org.pcmm.gates.IGateSpec.Direction;
import org.pcmm.gates.IPCMMGate;
import org.pcmm.gates.ISubscriberID;
import org.pcmm.gates.ITrafficProfile;
import org.pcmm.gates.ITransactionID;
import org.pcmm.gates.impl.AMID;
import org.pcmm.gates.impl.BestEffortService;
import org.pcmm.gates.impl.DOCSISServiceClassNameTrafficProfile;
import org.pcmm.gates.impl.ExtendedClassifier;
import org.pcmm.gates.impl.GateSpec;
import org.pcmm.gates.impl.PCMMGateReq;
import org.pcmm.gates.impl.SubscriberID;
import org.pcmm.gates.impl.TransactionID;
import org.pcmm.messages.IMessage;
import org.pcmm.messages.impl.MessageFactory;
import org.pcmm.objects.MMVersionInfo;
import org.pcmm.rcd.IPCMMPolicyServer;
import org.pcmm.state.IState;
import org.umu.cops.prpdp.COPSPdpConnection;
import org.umu.cops.prpdp.COPSPdpDataProcess;
import org.umu.cops.stack.COPSClientAcceptMsg;
import org.umu.cops.stack.COPSClientCloseMsg;
import org.umu.cops.stack.COPSClientOpenMsg;
import org.umu.cops.stack.COPSClientSI;
import org.umu.cops.stack.COPSData;
import org.umu.cops.stack.COPSException;
import org.umu.cops.stack.COPSHeader;
import org.umu.cops.stack.COPSMsg;
import org.umu.cops.stack.COPSReportMsg;

/**
 *
 * PCMM policy server
 *
 *
 */
public class PCMMPolicyServer extends AbstractPCMMClient implements
            IPCMMPolicyServer {

    private static final short MAX_PORT_NB = (short) 65535;
    private static final String DEFAULT_IP_MASK = "255.255.255.0";
    private short transactionID;
    private short classifierID;
    private int gateID;

    /*
     * (non-Javadoc)
     *
     * @see
     * org.pcmm.rcd.IPCMMPolicyServer#requestCMTSConnection(java.lang.String)
     */
    public Socket requestCMTSConnection(String host) {
        try {
            InetAddress address = InetAddress.getByName(host);
            return requestCMTSConnection(address);
        } catch (UnknownHostException e) {
            logger.severe(e.getMessage());
        }
        return null;
    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.pcmm.rcd.IPCMMPolicyServer#requestCMTSConnection(java.net.InetAddress
     * )
     */
    public Socket requestCMTSConnection(InetAddress host) {
        try {
            if (tryConnect(host, IANA_PORT)) {
                boolean endNegotiation = false;
                while (!endNegotiation) {
                    logger.info("waiting for OPN message from CMTS");
                    COPSMsg opnMessage = readMessage();
                    // Client-Close
                    if (opnMessage.getHeader().isAClientClose()) {
                        logger.info(((COPSClientCloseMsg) opnMessage)
                                    .getError().getDescription());
                        // close the socket
                        disconnect();
                        throw new COPSException("CMTS requetsed Client-Close");
                    } else // Client-Open
                        if (opnMessage.getHeader().isAClientOpen()) {
                            logger.info("OPN message received from CMTS");
                            COPSClientOpenMsg opn = (COPSClientOpenMsg) opnMessage;
                            if (opn.getClientSI() == null)
                                throw new COPSException(
                                    "CMTS shoud have sent MM version info in Client-Open message");
                            else {
                                // set the version info
                                MMVersionInfo vInfo = new MMVersionInfo(opn
                                                                        .getClientSI().getData().getData());
                                setVersionInfo(vInfo);
                                logger.info("CMTS sent MMVersion info : major:"
                                            + vInfo.getMajorVersionNB() + "  minor:"
                                            + vInfo.getMinorVersionNB()); //
                                if (getVersionInfo().getMajorVersionNB() == getVersionInfo()
                                        .getMinorVersionNB()) {
                                    // send a CC since CMTS has exhausted
                                    throw new COPSException(
                                        "CMTS exhausted all protocol selection attempts");
                                }
                            }
                            // send CAT response
                            Properties prop = new Properties();
                            logger.info("send CAT to the CMTS ");
                            COPSMsg catMsg = MessageFactory.getInstance().create(
                                                 COPSHeader.COPS_OP_CAT, prop);
                            sendRequest(catMsg);
                            // wait for REQ msg
                            COPSMsg reqMsg = readMessage();
                            // Client-Close
                            if (reqMsg.getHeader().isAClientClose()) {
                                logger.info(((COPSClientCloseMsg) opnMessage)
                                            .getError().getDescription());
                                // close the socket
                                throw new COPSException(
                                    "CMTS requetsed Client-Close");
                            } else // Request
                                if (reqMsg.getHeader().isARequest()) {
                                    logger.info("Received REQ message form CMTS");
                                    // end connection attempts

                                    COPSPdpDataProcess processor = null;
                                    COPSPdpConnection copsPdpConnection = new COPSPdpConnection(
                                        opn.getPepId(), getSocket(), processor);
                                    copsPdpConnection
                                    .setKaTimer(((COPSClientAcceptMsg) catMsg)
                                                .getKATimer().getTimerVal());
                                    new Thread(copsPdpConnection).start();

                                    endNegotiation = true;
                                } else
                                    throw new COPSException("Can't understand request");
                        } else {
                            throw new COPSException("Can't understand request");
                        }
                }
            }
            // else raise exception.
        } catch (Exception e) {
            logger.severe(e.getMessage());
            // no need to keep connection.
            disconnect();
            return null;
        }
        return getSocket();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.pcmm.rcd.IPCMMPolicyServer#gateSet()
     */
    @Override
    public boolean gateSet() {
        if (!isConnected())
            throw new IllegalArgumentException("Not connected");

        // setting up the gate
        IPCMMGate gate = new PCMMGateReq();
        // set transaction ID to gate set
        ITransactionID trID = new TransactionID();
        trID.setGateCommandType(ITransactionID.GateSet);
        transactionID = (short) (transactionID == 0 ? (short) (Math.random() * hashCode())
                                 : transactionID);
        trID.setTransactionIdentifier(transactionID);

        IAMID amid = new AMID();
        amid.setApplicationType((short) 0);
        amid.setApplicationMgrTag((short) 0);

        ISubscriberID subscriberID = new SubscriberID();
        subscriberID.setSourceIPAddress(getSocket().getLocalAddress());

        IGateSpec gateSpec = new GateSpec();
        gateSpec.setDirection(Direction.UPSTREAM);
        gateSpec.setDSCP_TOSOverwrite(DSCPTOS.OVERRIDE);

        // Authorized
        ITrafficProfile trafficProfile = new BestEffortService(
            BestEffortService.DEFAULT_ENVELOP);
        ((BestEffortService) trafficProfile).getAuthorizedEnvelop()
        .setTrafficPriority(BestEffortService.DEFAULT_TRAFFIC_PRIORITY);
        ((BestEffortService) trafficProfile).getAuthorizedEnvelop()
        .setMaximumTrafficBurst(
            BestEffortService.DEFAULT_MAX_TRAFFIC_BURST);

        IExtendedClassifier classifier = new ExtendedClassifier();
        // tcp => 6
        classifier.setProtocol(IClassifier.Protocol.TCP);
        classifier.setSourceIPAddress(getSocket().getLocalAddress());
        classifier.setDestinationIPAddress(getSocket().getInetAddress());
        try {
            classifier.setIPSourceMask(InetAddress.getByName(DEFAULT_IP_MASK));
            classifier.setIPDestinationMask(InetAddress
                                            .getByName(DEFAULT_IP_MASK));
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }

        classifier.setSourcePortStart((short) 0);
        classifier.setSourcePortEnd((short) MAX_PORT_NB);
        classifier.setDestinationPortStart((short) 0);
        classifier.setDestinationPortEnd((short) MAX_PORT_NB);
        // check if we have a stored value of classifierID else we just create
        // one
        classifier.setClassifierID((short) (classifierID == 0 ? Math.random()
                                            * hashCode() : classifierID));

        classifier.setActivationState((byte) 0);
        classifier.setAction((byte) 0);

        gate.setTransactionID(trID);
        gate.setAMID(amid);
        gate.setSubscriberID(subscriberID);
        gate.setGateSpec(gateSpec);
        gate.setClassifier(classifier);
        gate.setTrafficProfile(trafficProfile);
        Properties prop = new Properties();
        byte[] data = gate.getData();
        prop.put(IMessage.MessageProperties.GATE_CONTROL, new COPSData(data, 0,
                 data.length));
        prop.put(IMessage.MessageProperties.CLIENT_HANDLE, 0);
        COPSMsg dec = MessageFactory.getInstance().create(
                          COPSHeader.COPS_OP_DEC, prop);
        try {
            dec.dump(System.out);
            dec.writeData(getSocket());
        } catch (IOException unae) {
        }

        // sends the gate-set
        // sendRequest(dec);

        // waits for the gate-set-ack or error
        COPSMsg responseMsg = readMessage();
        if (responseMsg.getHeader().isAReport()) {
            logger.info("processing received report from CMTS");
            COPSReportMsg reportMsg = (COPSReportMsg) responseMsg;
            if (reportMsg.getClientSI().size() == 0) {
                return false;
            }
            COPSClientSI clientSI = (COPSClientSI) reportMsg.getClientSI()
                                    .elementAt(0);
            IPCMMGate responseGate = new PCMMGateReq(clientSI.getData()
                    .getData());
            if (((PCMMGateReq) responseGate).getError() != null) {

            }
            if (responseGate.getTransactionID() != null
                    && responseGate.getTransactionID().getGateCommandType() == ITransactionID.GateSetAck) {
                logger.info("the CMTS has sent a Gate-Set-Ack response");
                // here CMTS responded that he acknowledged the Gate-Set
                // TODO do further check of Gate-Set-Ack GateID etc...
                gateID = responseGate.getGateID().getGateID();
                return true;
            } else {
                return false;
            }
        }
        return false;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.pcmm.rcd.IPCMMPolicyServer#gateDelete()
     */
    @Override
    public boolean gateDelete() {
        IPCMMGate gate = new PCMMGateReq();
        // set transaction ID to gate delete
        ITransactionID trID = new TransactionID();
        trID.setGateCommandType(ITransactionID.GateDelete);
        transactionID = (short) (transactionID == 0 ? (short) (Math.random() * hashCode())
                                 : transactionID);
        trID.setTransactionIdentifier(transactionID);
        return false;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.pcmm.rcd.IPCMMPolicyServer#gateInfo()
     */
    @Override
    public boolean gateInfo() {
        IPCMMGate gate = new PCMMGateReq();
        // set transaction ID to gate info
        ITransactionID trID = new TransactionID();
        trID.setGateCommandType(ITransactionID.GateInfo);
        transactionID = (short) (transactionID == 0 ? (short) (Math.random() * hashCode())
                                 : transactionID);
        trID.setTransactionIdentifier(transactionID);
        return false;
    }

    /*
     * (non-Javadoc)
     *
     * @see org.pcmm.rcd.IPCMMPolicyServer#synchronize()
     */
    @Override
    public boolean synchronize() {
        IPCMMGate gate = new PCMMGateReq();
        // set transaction ID to synch request
        ITransactionID trID = new TransactionID();
        trID.setGateCommandType(ITransactionID.SynchRequest);
        transactionID = (short) (transactionID == 0 ? (short) (Math.random() * hashCode())
                                 : transactionID);
        trID.setTransactionIdentifier(transactionID);
        return false;
    }

    public PCMMPolicyServer() {
        super();
    }

    /*
     * (non-Javadoc)
     *
     * @see org.pcmm.rcd.IPCMMServer#startServer()
     */
    public void startServer() {
        // TODO not needed yet

    }

    /*
     * (non-Javadoc)
     *
     * @see org.pcmm.rcd.IPCMMServer#stopServer()
     */
    public void stopServer() {
        // TODO not needed yet

    }

    /*
     * (non-Javadoc)
     *
     * @see org.pcmm.state.IStateful#recordState()
     */
    public void recordState() {

    }

    /*
     * (non-Javadoc)
     *
     * @see org.pcmm.state.IStateful#getRecoredState()
     */
    public IState getRecoredState() {
        return null;
    }

}
