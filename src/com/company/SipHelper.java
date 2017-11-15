/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.company;

import gov.nist.javax.sip.SipStackExt;
import gov.nist.javax.sip.clientauthutils.AccountManager;
import gov.nist.javax.sip.clientauthutils.AuthenticationHelper;
import gov.nist.javax.sip.header.To;
import gov.nist.javax.sip.header.extensions.ReferencesHeader;
import gov.nist.javax.sip.header.extensions.ReferredByHeader;
import gov.nist.javax.sip.header.extensions.ReplacesHeader;

import javax.sip.*;
import javax.sip.address.Address;
import javax.sip.address.AddressFactory;
import javax.sip.address.SipURI;
import javax.sip.header.*;
import javax.sip.message.Message;
import javax.sip.message.MessageFactory;
import javax.sip.message.Request;
import javax.sip.message.Response;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.EventObject;
import java.util.List;

/**
 * Helper class for holding SIP stack related classes and for various low-level
 * SIP tasks like sending messages.
 */
public class SipHelper {
    private static final String TAG = SipHelper.class.getSimpleName();
    private static final boolean DBG = false;
    private static final boolean DBG_PING = false;

    private SipStack mSipStack;
    private SipProvider mSipProvider;
    private AddressFactory mAddressFactory;
    private HeaderFactory mHeaderFactory;
    private MessageFactory mMessageFactory;

    public SipHelper(SipStack sipStack, SipProvider sipProvider)
            throws PeerUnavailableException {
        mSipStack = sipStack;
        mSipProvider = sipProvider;

        SipFactory sipFactory = SipFactory.getInstance();
        mAddressFactory = sipFactory.createAddressFactory();
        mHeaderFactory = sipFactory.createHeaderFactory();
        mMessageFactory = sipFactory.createMessageFactory();
    }

    private FromHeader createFromHeader(SipURI profile, String tag)
            throws ParseException {
        return mHeaderFactory.createFromHeader(createAddress(profile), tag);
    }

    private ToHeader createToHeader(SipURI profile) throws ParseException {
        return createToHeader(profile, null);
    }

    private ToHeader createToHeader(SipURI profile, String tag)
            throws ParseException {
        return mHeaderFactory.createToHeader(createAddress(profile), tag);
    }

    private CSeqHeader createCSeqHeader(String method)
            throws ParseException, InvalidArgumentException {
        long sequence = (long) (Math.random() * 10000);
        return mHeaderFactory.createCSeqHeader(sequence, method);
    }

    private MaxForwardsHeader createMaxForwardsHeader()
            throws InvalidArgumentException {
        return mHeaderFactory.createMaxForwardsHeader(70);
    }

    private MaxForwardsHeader createMaxForwardsHeader(int max)
            throws InvalidArgumentException {
        return mHeaderFactory.createMaxForwardsHeader(max);
    }

    private ListeningPoint getListeningPoint() throws SipException {
        ListeningPoint lp = mSipProvider.getListeningPoint(ListeningPoint.UDP);
        if (lp == null) lp = mSipProvider.getListeningPoint(ListeningPoint.TCP);
        if (lp == null) {
            ListeningPoint[] lps = mSipProvider.getListeningPoints();
            if ((lps != null) && (lps.length > 0)) lp = lps[0];
        }
        if (lp == null) {
            throw new SipException("no listening point is available");
        }
        return lp;
    }

    private List<ViaHeader> createViaHeaders()
            throws ParseException, SipException, InvalidArgumentException {
        List<ViaHeader> viaHeaders = new ArrayList<ViaHeader>(1);
        ListeningPoint lp = getListeningPoint();
        ViaHeader viaHeader = mHeaderFactory.createViaHeader(lp.getIPAddress(),
                lp.getPort(), lp.getTransport(), null);
        viaHeader.setRPort();
        viaHeaders.add(viaHeader);
        return viaHeaders;
    }

    private ContactHeader createContactHeader(SipURI profile)
            throws ParseException, SipException {
        return createContactHeader(profile, null, 0);
    }

    private Address createAddress(SipURI profile) throws ParseException {
        SipURI uri = mAddressFactory.createSipURI(profile.getUser(), profile.getHost());
        uri.setPort(profile.getPort());
        return mAddressFactory.createAddress(uri);
    }

    private ContactHeader createContactHeader(SipURI profile,
                                              String ip, int port) throws ParseException,
            SipException {
        SipURI contactURI = (ip == null)
                ? createSipUri(profile.getUser(), profile.getTransportParam(),
                        getListeningPoint())
                : createSipUri(profile.getUser(), profile.getTransportParam(),
                        ip, port);

        Address contactAddress = mAddressFactory.createAddress(contactURI);
        contactAddress.setDisplayName(contactAddress.getDisplayName());

        return mHeaderFactory.createContactHeader(contactAddress);
    }

    private ContactHeader createWildcardContactHeader() {
        ContactHeader contactHeader  = mHeaderFactory.createContactHeader();
        contactHeader.setWildCard();
        return contactHeader;
    }

    private SipURI createSipUri(String username, String transport,
                                ListeningPoint lp) throws ParseException {
        return createSipUri(username, transport, lp.getIPAddress(), lp.getPort());
    }

    private SipURI createSipUri(String username, String transport,
                                String ip, int port) throws ParseException {
        SipURI uri = mAddressFactory.createSipURI(username, ip);
        uri.setPort(port);
        uri.setTransportParam(transport);
        return uri;
    }

    public ClientTransaction sendOptions(SipURI caller, SipURI callee,
                                         String tag, CallIdHeader callIdHeader) throws SipException {
        try {
            Request request = (caller == callee)
                    ? createRequest(Request.OPTIONS, caller, tag, callIdHeader)
                    : createRequest(Request.OPTIONS, caller, callee, tag, callIdHeader);

            ClientTransaction clientTransaction =
                    mSipProvider.getNewClientTransaction(request);
            clientTransaction.sendRequest();
            return clientTransaction;
        } catch (Exception e) {
            throw new SipException("sendOptions()", e);
        }
    }

    public ClientTransaction sendRegister(SipURI userProfile, String tag,
                                          int expiry, CallIdHeader callIdHeader) throws SipException {
        try {
            Request request = createRequest(Request.REGISTER, userProfile, tag, callIdHeader);
            if (expiry == 0) {
                // remove all previous registrations by wildcard
                // rfc3261#section-10.2.2
                request.addHeader(createWildcardContactHeader());
            } else {
                request.addHeader(createContactHeader(userProfile));
            }
            request.addHeader(mHeaderFactory.createExpiresHeader(expiry));

            ClientTransaction clientTransaction =
                    mSipProvider.getNewClientTransaction(request);
            clientTransaction.sendRequest();
            return clientTransaction;
        } catch (ParseException | InvalidArgumentException e) {
            throw new SipException("sendRegister()", e);
        }
    }

    private Request createRequest(String requestType, SipURI userProfile,
                                  String tag, CallIdHeader callIdHeader)
            throws ParseException, SipException, InvalidArgumentException {
        FromHeader fromHeader = createFromHeader(userProfile, tag);
        ToHeader toHeader = createToHeader(userProfile);

        List<ViaHeader> viaHeaders = createViaHeaders();
        CSeqHeader cSeqHeader = createCSeqHeader(requestType);
        MaxForwardsHeader maxForwards = createMaxForwardsHeader();
        Request request = mMessageFactory.createRequest(userProfile,
                requestType, callIdHeader, cSeqHeader, fromHeader,
                toHeader, viaHeaders, maxForwards);
        Header userAgentHeader = mHeaderFactory.createHeader("User-Agent",
                "im.dlg.sip/0.1");
        request.addHeader(userAgentHeader);
        return request;
    }

    public ClientTransaction handleChallenge(ResponseEvent responseEvent,
                                             AccountManager accountManager) throws SipException {
        AuthenticationHelper authenticationHelper =
                ((SipStackExt) mSipStack).getAuthenticationHelper(
                        accountManager, mHeaderFactory);
        ClientTransaction tid = responseEvent.getClientTransaction();
        ClientTransaction ct = authenticationHelper.handleChallenge(
                responseEvent.getResponse(), tid, mSipProvider, 5, true);
        if (DBG) log("send request with challenge response: "
                + ct.getRequest());
        ct.sendRequest();
        return ct;
    }

    private Request createRequest(String requestType, SipURI caller,
                                  SipURI callee, String tag, CallIdHeader callIdHeader)
            throws ParseException, SipException, InvalidArgumentException {
        FromHeader fromHeader = createFromHeader(caller, tag);
        ToHeader toHeader = createToHeader(callee);
        List<ViaHeader> viaHeaders = createViaHeaders();
        CSeqHeader cSeqHeader = createCSeqHeader(requestType);
        MaxForwardsHeader maxForwards = createMaxForwardsHeader();

        Request request = mMessageFactory.createRequest(callee,
                requestType, callIdHeader, cSeqHeader, fromHeader,
                toHeader, viaHeaders, maxForwards);

        request.addHeader(createContactHeader(caller));
        return request;
    }

    public ClientTransaction sendInvite(SipURI caller, SipURI callee,
                                        String sessionDescription, String tag, ReferredByHeader referredBy,
                                        String replaces, CallIdHeader callIdHeader) throws SipException {
        try {
            Request request = createRequest(Request.INVITE, caller, callee, tag, callIdHeader);
            if (referredBy != null) request.addHeader(referredBy);
            if (replaces != null) {
                request.addHeader(mHeaderFactory.createHeader(
                        ReplacesHeader.NAME, replaces));
            }
            request.setContent(sessionDescription,
                    mHeaderFactory.createContentTypeHeader(
                            "application", "sdp"));
            ClientTransaction clientTransaction =
                    mSipProvider.getNewClientTransaction(request);
            if (DBG) log("send INVITE: " + request);
            clientTransaction.sendRequest();
            return clientTransaction;
        } catch (ParseException | InvalidArgumentException e) {
            throw new SipException("sendInvite()", e);
        }
    }

    public ClientTransaction sendReinvite(Dialog dialog,
                                          String sessionDescription) throws SipException {
        try {
            Request request = dialog.createRequest(Request.INVITE);
            request.setContent(sessionDescription,
                    mHeaderFactory.createContentTypeHeader(
                            "application", "sdp"));

            // Adding rport argument in the request could fix some SIP servers
            // in resolving the initiator's NAT port mapping for relaying the
            // response message from the other end.

            ViaHeader viaHeader = (ViaHeader) request.getHeader(ViaHeader.NAME);
            if (viaHeader != null) viaHeader.setRPort();

            ClientTransaction clientTransaction =
                    mSipProvider.getNewClientTransaction(request);
            if (DBG) log("send RE-INVITE: " + request);
            dialog.sendRequest(clientTransaction);
            return clientTransaction;
        } catch (ParseException | InvalidArgumentException e) {
            throw new SipException("sendReinvite()", e);
        }
    }

    public ServerTransaction getServerTransaction(RequestEvent event)
            throws SipException {
        ServerTransaction transaction = event.getServerTransaction();
        if (transaction == null) {
            Request request = event.getRequest();
            return mSipProvider.getNewServerTransaction(request);
        } else {
            return transaction;
        }
    }

    /**
     * @param event the INVITE request event
     */
    public ServerTransaction sendRinging(RequestEvent event, String tag)
            throws SipException {
        try {
            Request request = event.getRequest();
            ServerTransaction transaction = getServerTransaction(event);

            Response response = mMessageFactory.createResponse(Response.RINGING,
                    request);

            ToHeader toHeader = (ToHeader) response.getHeader(ToHeader.NAME);
            toHeader.setTag(tag);
            response.addHeader(toHeader);
            if (DBG) log("send RINGING: " + response);
            transaction.sendResponse(response);
            return transaction;
        } catch (ParseException | InvalidArgumentException e) {
            throw new SipException("sendRinging()", e);
        }
    }

    /**
     * @param event the INVITE request event
     */
    public ServerTransaction sendInviteOk(RequestEvent event,
                                          SipURI localProfile, String sessionDescription,
                                          ServerTransaction inviteTransaction) throws SipException {
        try {
            Request request = event.getRequest();
            Response response = mMessageFactory.createResponse(Response.OK,
                    request);
            response.addHeader(createContactHeader(localProfile));
            response.setContent(sessionDescription,
                    mHeaderFactory.createContentTypeHeader(
                            "application", "sdp"));

            if (inviteTransaction == null) {
                inviteTransaction = getServerTransaction(event);
            }

            if (inviteTransaction.getState() != TransactionState.COMPLETED) {
                if (DBG) log("send OK: " + response);
                inviteTransaction.sendResponse(response);
            }

            return inviteTransaction;
        } catch (ParseException | InvalidArgumentException e) {
            throw new SipException("sendInviteOk()", e);
        }
    }

    /**
     * @param event the INVITE request event
     */
    public ServerTransaction sendInviteOk(RequestEvent event,
                                          SipURI localProfile, String sessionDescription,
                                          ServerTransaction inviteTransaction, String externalIp,
                                          int externalPort) throws SipException {
        try {
            Request request = event.getRequest();
            Response response = mMessageFactory.createResponse(Response.OK,
                    request);
            response.addHeader(createContactHeader(localProfile, externalIp,
                    externalPort));
            response.setContent(sessionDescription,
                    mHeaderFactory.createContentTypeHeader(
                            "application", "sdp"));

            if (inviteTransaction == null) {
                inviteTransaction = getServerTransaction(event);
            }

            if (inviteTransaction.getState() != TransactionState.COMPLETED) {
                if (DBG) log("send OK: " + response);
                inviteTransaction.sendResponse(response);
            }

            return inviteTransaction;
        } catch (ParseException | InvalidArgumentException e) {
            throw new SipException("sendInviteOk()", e);
        }
    }

    public void sendInviteBusyHere(RequestEvent event,
            ServerTransaction inviteTransaction) throws SipException {
        try {
            Request request = event.getRequest();
            Response response = mMessageFactory.createResponse(
                    Response.BUSY_HERE, request);

            if (inviteTransaction == null) {
                inviteTransaction = getServerTransaction(event);
            }

            if (inviteTransaction.getState() != TransactionState.COMPLETED) {
                if (DBG) log("send BUSY HERE: " + response);
                inviteTransaction.sendResponse(response);
            }
        } catch (ParseException | InvalidArgumentException e) {
            throw new SipException("sendInviteBusyHere()", e);
        }
    }

    /**
     * @param event the INVITE ACK request event
     */
    public void sendInviteAck(ResponseEvent event, Dialog dialog)
            throws SipException {
        try {
            Response response = event.getResponse();
            long cseq = ((CSeqHeader) response.getHeader(CSeqHeader.NAME))
                    .getSeqNumber();
            Request ack = dialog.createAck(cseq);
            if (DBG) log("send ACK: " + ack);
            dialog.sendAck(ack);
        } catch (InvalidArgumentException e) {
            throw new SipException("sendInviteAck()", e);
        }
    }

    public void sendBye(Dialog dialog) throws SipException {
        Request byeRequest = dialog.createRequest(Request.BYE);
        if (DBG) log("send BYE: " + byeRequest);
        dialog.sendRequest(mSipProvider.getNewClientTransaction(byeRequest));
    }

    public void sendCancel(ClientTransaction inviteTransaction)
            throws SipException {
        Request cancelRequest = inviteTransaction.createCancel();
        if (DBG) log("send CANCEL: " + cancelRequest);
        mSipProvider.getNewClientTransaction(cancelRequest).sendRequest();
    }

    public void sendResponse(RequestEvent event, int responseCode)
            throws SipException {
        try {
            Request request = event.getRequest();
            Response response = mMessageFactory.createResponse(
                    responseCode, request);
            if (DBG && (!Request.OPTIONS.equals(request.getMethod())
                    || DBG_PING)) {
                log("send response: " + response);
            }
            getServerTransaction(event).sendResponse(response);
        } catch (ParseException | InvalidArgumentException e) {
            throw new SipException("sendResponse()", e);
        }
    }

    public void sendReferNotify(Dialog dialog, String content)
            throws SipException {
        try {
            Request request = dialog.createRequest(Request.NOTIFY);
            request.addHeader(mHeaderFactory.createSubscriptionStateHeader(
                    "active;expires=60"));
            // set content here
            request.setContent(content,
                    mHeaderFactory.createContentTypeHeader(
                            "message", "sipfrag"));
            request.addHeader(mHeaderFactory.createEventHeader(
                    ReferencesHeader.REFER));
            if (DBG) log("send NOTIFY: " + request);
            dialog.sendRequest(mSipProvider.getNewClientTransaction(request));
        } catch (ParseException e) {
            throw new SipException("sendReferNotify()", e);
        }
    }

    public void sendInviteRequestTerminated(Request inviteRequest,
            ServerTransaction inviteTransaction) throws SipException {
        try {
            Response response = mMessageFactory.createResponse(
                    Response.REQUEST_TERMINATED, inviteRequest);
            if (DBG) log("send response: " + response);
            inviteTransaction.sendResponse(response);
        } catch (ParseException | InvalidArgumentException e) {
            throw new SipException("sendInviteRequestTerminated()", e);
        }
    }

    public static String getTo(EventObject event) {
        if (event == null) return null;
        if (event instanceof RequestEvent) {
            return getTo(((RequestEvent) event).getRequest().getHeader("To"));
        } else if (event instanceof ResponseEvent) {
            return getTo(((ResponseEvent) event).getResponse().getHeader("To"));
        }
        return null;
    }

    public static String getTo(Header header) {
        return ((To) header).getUserAtHostPort();
    }

    public static String getCallId(EventObject event) {
        if (event == null) return null;
        if (event instanceof RequestEvent) {
            return getCallId(((RequestEvent) event).getRequest());
        } else if (event instanceof ResponseEvent) {
            return getCallId(((ResponseEvent) event).getResponse());
        } else if (event instanceof DialogTerminatedEvent) {
            Dialog dialog = ((DialogTerminatedEvent) event).getDialog();
            return getCallId(((DialogTerminatedEvent) event).getDialog());
        } else if (event instanceof TransactionTerminatedEvent) {
            TransactionTerminatedEvent e = (TransactionTerminatedEvent) event;
            return getCallId(e.isServerTransaction()
                    ? e.getServerTransaction()
                    : e.getClientTransaction());
        } else {
            Object source = event.getSource();
            if (source instanceof Transaction) {
                return getCallId(((Transaction) source));
            } else if (source instanceof Dialog) {
                return getCallId((Dialog) source);
            }
        }
        return "";
    }

    public static String getCallId(Transaction transaction) {
        return ((transaction != null) ? getCallId(transaction.getRequest())
                                      : "");
    }

    private static String getCallId(Message message) {
        CallIdHeader callIdHeader =
                (CallIdHeader) message.getHeader(CallIdHeader.NAME);
        return callIdHeader.getCallId();
    }

    private static String getCallId(Dialog dialog) {
        return dialog.getCallId().getCallId();
    }

    private void log(String s) {

    }
}
