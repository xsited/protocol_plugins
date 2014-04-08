/**
 @header@
 */
package org.pcmm.rcd.impl;

import java.net.Socket;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Properties;
import java.util.Vector;
import java.util.concurrent.Callable;

import org.pcmm.messages.impl.MessageFactory;
import org.pcmm.rcd.ICMTS;
import org.umu.cops.prpep.COPSPepConnection;
import org.umu.cops.prpep.COPSPepException;
import org.umu.cops.prpep.COPSPepReqStateMan;
import org.umu.cops.stack.COPSAcctTimer;
import org.umu.cops.stack.COPSClientAcceptMsg;
import org.umu.cops.stack.COPSClientCloseMsg;
import org.umu.cops.stack.COPSContext;
import org.umu.cops.stack.COPSDecision;
import org.umu.cops.stack.COPSDecisionMsg;
import org.umu.cops.stack.COPSHeader;
import org.umu.cops.stack.COPSKATimer;
import org.umu.cops.stack.COPSMsg;
import org.umu.cops.stack.COPSPrObjBase;
import org.umu.cops.stack.COPSReqMsg;

/**
 *
 */
public class CMTS extends AbstractPCMMServer implements ICMTS {

	public CMTS() {
		super();
	}

	@Override
	protected IPCMMClientHandler getPCMMClientHandler(final Socket socket) {

		return new AbstractPCMMClientHandler(socket) {

			private String handle;

			public void run() {
				try {
					// send OPN message
					// set the major version info and minor version info to
					// default (5,0)
					logger.info("Send OPN message to the PS");
					sendRequest(MessageFactory.getInstance().create(COPSHeader.COPS_OP_OPN, new Properties()));
					// wait for CAT
					COPSMsg recvMsg = readMessage();

					if (recvMsg.getHeader().isAClientClose()) {
						COPSClientCloseMsg cMsg = (COPSClientCloseMsg) recvMsg;
						logger.info("PS requested Client-Close" + cMsg.getError().getDescription());
						// send a CC message and close the socket
						disconnect();
						return;
					}
					if (recvMsg.getHeader().isAClientAccept()) {
						logger.info("received Client-Accept from PS");
						COPSClientAcceptMsg cMsg = (COPSClientAcceptMsg) recvMsg;
						// Support
						if (cMsg.getIntegrity() != null) {
							throw new COPSPepException("Unsupported object (Integrity)");
						}

						// Mandatory KATimer
						COPSKATimer kt = cMsg.getKATimer();
						if (kt == null)
							throw new COPSPepException("Mandatory COPS object missing (KA Timer)");
						short kaTimeVal = kt.getTimerVal();

						// ACTimer
						COPSAcctTimer at = cMsg.getAcctTimer();
						short acctTimer = 0;
						if (at != null)
							acctTimer = at.getTimerVal();

						logger.info("Send a REQ message to the PS");
						{
							Properties prop = new Properties();
							COPSMsg reqMsg = MessageFactory.getInstance().create(COPSHeader.COPS_OP_REQ, prop);
							handle = ((COPSReqMsg) reqMsg).getClientHandle().getId().str();
							sendRequest(reqMsg);
						}
						// Create the connection manager
						PCMMCmtsConnection conn = new PCMMCmtsConnection(CLIENT_TYPE, socket);
						// pcmm specific handler
						conn.addReqStateMgr(handle, new PCMMPSReqStateMan(CLIENT_TYPE, handle));
						conn.setKaTimer(kaTimeVal);
						conn.setAcctTimer(acctTimer);
						logger.info(getClass().getName() + " Thread(conn).start");
						new Thread(conn).start();
					} else {
						// messages of other types are not expected
						throw new COPSPepException("Message not expected. Closing connection for " + socket.toString());
					}
				} catch (COPSPepException e) {
					logger.error(e.getMessage());
				}
			}

			@Override
			public void task(Callable<?> c) {
				// TODO Auto-generated method stub

			}

			@Override
			public void shouldWait(int t) {
				// TODO Auto-generated method stub

			}

			@Override
			public void done() {
				// TODO Auto-generated method stub

			}

		};
	}

	/* public */class PCMMCmtsConnection extends COPSPepConnection {

		public PCMMCmtsConnection(short clientType, Socket sock) {
			super(clientType, sock);
		}

		public void addReqStateMgr(String hanlde, COPSPepReqStateMan r) {
			// map < String(COPSHandle), COPSPepReqStateMan>;
			getReqStateMans().put(hanlde, r);
		}
	}

	class PCMMPSReqStateMan extends COPSPepReqStateMan {

		public PCMMPSReqStateMan(short clientType, String clientHandle) {
			super(clientType, clientHandle);
		}

		@Override
		protected void processDecision(COPSDecisionMsg dMsg)
				throws COPSPepException {

			// COPSHandle handle = dMsg.getClientHandle();
			Hashtable decisions = dMsg.getDecisions();

			Hashtable removeDecs = new Hashtable(40);
			Hashtable installDecs = new Hashtable(40);
			Hashtable errorDecs = new Hashtable(40);
			for (Enumeration e = decisions.keys(); e.hasMoreElements();) {

				COPSContext context = (COPSContext) e.nextElement();
				Vector v = (Vector) decisions.get(context);
				Enumeration ee = v.elements();
				COPSDecision cmddecision = (COPSDecision) ee.nextElement();

				// cmddecision --> we must check whether it is an error!

				if (cmddecision.isInstallDecision()) {
					String prid = new String();
					for (; ee.hasMoreElements();) {
						COPSDecision decision = (COPSDecision) ee.nextElement();

						COPSPrObjBase obj = new COPSPrObjBase(decision.getData().getData());
						switch (obj.getSNum()) {
						case COPSPrObjBase.PR_PRID:
							prid = obj.getData().str();
						break;
						case COPSPrObjBase.PR_EPD:
							installDecs.put(prid, obj.getData().str());
						break;
						default:
						break;
						}
					}
				}

				if (cmddecision.isRemoveDecision()) {

					String prid = new String();
					for (; ee.hasMoreElements();) {
						COPSDecision decision = (COPSDecision) ee.nextElement();

						COPSPrObjBase obj = new COPSPrObjBase(decision.getData().getData());
						switch (obj.getSNum()) {
						case COPSPrObjBase.PR_PRID:
							prid = obj.getData().str();
						break;
						case COPSPrObjBase.PR_EPD:
							removeDecs.put(prid, obj.getData().str());
						break;
						default:
						break;
						}
					}
				}
			}
			if (_process != null) {
				// ** Apply decisions to the configuration
				_process.setDecisions(this, removeDecs, installDecs, errorDecs);
				_status = ST_DECS;

				if (_process.isFailReport(this)) {
					// COPSDebug.out(getClass().getName(),"Sending FAIL Report\n");
					_sender.sendFailReport(_process.getReportData(this));
				} else {
					// COPSDebug.out(getClass().getName(),"Sending SUCCESS Report\n");
					_sender.sendSuccessReport(_process.getReportData(this));
				}
				_status = ST_REPORT;

				if (!_syncState) {
					_sender.sendSyncComplete();
					_syncState = true;
					_status = ST_SYNCALL;
				}
			}
		}
	}

}
