/**
 *
 */
package org.pcmm.rcd.impl;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.Assert;
import org.pcmm.messages.impl.MessageFactory;
import org.pcmm.rcd.IPCMMServer;
import org.pcmm.state.IState;
import org.umu.cops.stack.COPSHeader;
import org.umu.cops.stack.COPSMsg;

/*
 * (non-Javadoc)
 *
 * @see pcmm.rcd.IPCMMServer
 */
public abstract class AbstractPCMMServer implements IPCMMServer {
    private Logger logger;
    /*
     * A ServerSocket to accept messages ( OPN requests)
     */
    private ServerSocket serverSocket;

    private Socket stopSocket;

    private volatile boolean keepAlive;
    /*
     *
     */
    private int port;

    // private Map<Socket, IPCMMClientHandler> handlersPool = new
    // HashMap<Socket, IPCMMServer.IPCMMClientHandler>();

    protected AbstractPCMMServer() {
        this(DEFAULT_LISTENING_PORT);
    }

    protected AbstractPCMMServer(int port) {
        Assert.assertTrue(port >= 0 && port <= 65535);
        this.port = port;
        keepAlive = true;
        logger = Logger.getLogger(getClass().getName());
        logger.setLevel(Level.SEVERE);
        try {
            serverSocket = new ServerSocket(port);
        } catch (IOException e) {
            logger.severe(e.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see pcmm.rcd.IPCMMServer#startServer()
     */
    public void startServer() {
        new Thread(new Runnable() {
            public void run() {
                while (keepAlive) {
                    try {
                        Socket socket = serverSocket.accept();
                        if (keepAlive)
                            new Thread(getPCMMClientHandler(socket)).start();
                        else
                            socket.close();
                    } catch (IOException e) {
                        logger.severe(e.getMessage());
                    }
                }
                try {
                    if (stopSocket != null && stopSocket.isConnected())
                        stopSocket.close();
                    if (serverSocket != null && serverSocket.isBound())
                        serverSocket.close();
                } catch (IOException e) {
                    logger.severe(e.getMessage());
                }
            }
        }).start();
    }

    protected abstract IPCMMClientHandler getPCMMClientHandler(Socket socket);

    /*
     * (non-Javadoc)
     *
     * @see pcmm.rcd.IPCMMServer#stopServer()
     */
    public void stopServer() {
        keepAlive = false;
        try {
            stopSocket = new Socket(serverSocket.getInetAddress(),
                                    serverSocket.getLocalPort());
        } catch (Exception e) {
            logger.severe(e.getMessage());
        }
    }

    /*
     * (non-Javadoc)
     *
     * @see pcmm.state.IStateful#recordState()
     */
    public void recordState() {

    }

    /*
     * (non-Javadoc)
     *
     * @see pcmm.state.IStateful#getRecoredState()
     */
    public IState getRecoredState() {
        return null;
    }

    /**
     * @return the serverSocket
     */
    public ServerSocket getServerSocket() {
        return serverSocket;
    }

    /**
     * @param serverSocket
     *            the serverSocket to set
     */
    public void setServerSocket(ServerSocket serverSocket) {
        this.serverSocket = serverSocket;
    }

    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }

    /**
     * @param port
     *            the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /*
     * (non-Javadoc)
     *
     * @see pcmm.rcd.IPCMMServer.IPCMMClientHandler
     */
    public abstract class AbstractPCMMClientHandler extends AbstractPCMMClient
                implements IPCMMClientHandler {

        protected boolean sendCCMessage = false;

        public AbstractPCMMClientHandler(Socket socket) {
            super();
            setSocket(socket);
        }

        @Override
        public boolean disconnect() {
            // XXX send CC message
            sendCCMessage = true;
            /*
             * is this really needed ?
             */
            // if (getSocket() != null)
            // handlersPool.remove(getSocket());
            COPSMsg message = MessageFactory.getInstance().create(
                                  COPSHeader.COPS_OP_CC);
            sendRequest(message);
            return super.disconnect();
        }

    }

}
