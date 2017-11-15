package com.company;

import gov.nist.javax.sip.address.SipUri;
import gov.nist.javax.sip.stack.NioMessageProcessorFactory;
import org.apache.log4j.BasicConfigurator;

import javax.sip.*;
import javax.sip.address.SipURI;
import javax.sip.header.CallIdHeader;
import java.text.ParseException;
import java.util.Properties;
import java.util.TooManyListenersException;

public class Main implements SipListener {
    private final SipProvider sipProvider;
    private final SipHelper sipHelper;

    private Main() throws PeerUnavailableException,
            InvalidArgumentException, TransportNotSupportedException,
            ObjectInUseException, TooManyListenersException {
        BasicConfigurator.configure();

        Properties properties = new Properties();

        // These properties are final
        properties.setProperty("javax.sip.STACK_NAME", "im.dlg.sip");
        properties.setProperty("javax.sip.AUTOMATIC_DIALOG_SUPPORT", "on");
        properties.setProperty("gov.nist.javax.sip.DELIVER_RETRANSMITTED_ACK_TO_LISTENER", "true");
        properties.setProperty("gov.nist.javax.sip.REENTRANT_LISTENER", "false");
        properties.setProperty("gov.nist.javax.sip.MESSAGE_PROCESSOR_FACTORY", NioMessageProcessorFactory.class.getCanonicalName());
        properties.setProperty("gov.nist.javax.sip.TRACE_LEVEL", "32");

        SipStack sipStack = SipFactory.getInstance().createSipStack(properties);

        ListeningPoint listeningPoint = sipStack.createListeningPoint("127.0.0.1", 14000, "wss");
        sipProvider = sipStack.createSipProvider(listeningPoint);
        sipProvider.addSipListener(this);

        sipHelper = new SipHelper(sipStack, sipProvider);
    }

    public void test() throws ParseException {
        SipURI sipURI1 = new SipUri();
        sipURI1.setUser("1");
        sipURI1.setUserPassword("1");
        sipURI1.setHost("test-dialog.mastervoice.it");
        sipURI1.setPort(8443);
        sipURI1.setTransportParam("wss");
        sipURI1.setMethodParam("GET");
        sipURI1.setHeader("host", "test-dialog.mastervoice.it:8443");
        new SIPRegisterThread(sipProvider, sipHelper, sipURI1).start();
        SipURI sipURI2 = new SipUri();
        sipURI2.setUser("2");
        sipURI2.setUserPassword("2");
        sipURI2.setHost("test-dialog.mastervoice.it");
        sipURI2.setPort(8443);
        sipURI2.setTransportParam("wss");
        sipURI2.setMethodParam("GET");
        sipURI2.setHeader("host", "test-dialog.mastervoice.it:8443");
        new SIPRegisterThread(sipProvider, sipHelper, sipURI2).start();
    }

    public static void main(String[] args) throws InvalidArgumentException,
            TransportNotSupportedException, TooManyListenersException,
            PeerUnavailableException, ObjectInUseException {
        try {
            new Main().test();
        } catch (ParseException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void processRequest(RequestEvent requestEvent) {
        System.out.println(requestEvent);
    }

    @Override
    public void processResponse(ResponseEvent responseEvent) {
        System.out.println(responseEvent);
    }

    @Override
    public void processTimeout(TimeoutEvent timeoutEvent) {
        System.out.println(timeoutEvent);
    }

    @Override
    public void processIOException(IOExceptionEvent ioExceptionEvent) {
        System.out.println(ioExceptionEvent);
    }

    @Override
    public void processTransactionTerminated(TransactionTerminatedEvent transactionTerminatedEvent) {
        System.out.println(transactionTerminatedEvent);
    }

    @Override
    public void processDialogTerminated(DialogTerminatedEvent dialogTerminatedEvent) {
        System.out.println(dialogTerminatedEvent);
    }

    private static class SIPRegisterThread extends Thread {

        private final SipProvider sipProvider;
        private final SipHelper sipHelper;
        private final SipURI localProfile;

        private SIPRegisterThread(SipProvider sipProvider, SipHelper sipHelper, SipURI localProfile) {
            this.sipProvider = sipProvider;
            this.sipHelper = sipHelper;
            this.localProfile = localProfile;
        }

        @Override
        public void run() {
            CallIdHeader callIdHeader = sipProvider.getNewCallId();
            try {
                ClientTransaction mClientTransaction = sipHelper.sendRegister(
                        localProfile, String.valueOf(Math.random() * 0x100000000L),
                        3600, callIdHeader);
            } catch (SipException e) {
                e.printStackTrace();
            }
        }
    }
}

