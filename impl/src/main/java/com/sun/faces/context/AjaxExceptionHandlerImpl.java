/*
 * Copyright (c) 1997, 2018 Oracle and/or its affiliates. All rights reserved.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0, which is available at
 * http://www.eclipse.org/legal/epl-2.0.
 *
 * This Source Code may also be made available under the following Secondary
 * Licenses when the conditions for such availability set forth in the
 * Eclipse Public License v. 2.0 are satisfied: GNU General Public License,
 * version 2 with the GNU Classpath Exception, which is available at
 * https://www.gnu.org/software/classpath/license.html.
 *
 * SPDX-License-Identifier: EPL-2.0 OR GPL-2.0 WITH Classpath-exception-2.0
 */

package com.sun.faces.context;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.faces.FacesException;
import javax.faces.application.ProjectStage;
import javax.faces.component.UIComponent;
import javax.faces.context.ExceptionHandler;
import javax.faces.context.ExceptionHandlerWrapper;
import javax.faces.context.ExternalContext;
import javax.faces.context.FacesContext;
import javax.faces.context.PartialResponseWriter;
import javax.faces.event.AbortProcessingException;
import javax.faces.event.ExceptionQueuedEvent;
import javax.faces.event.ExceptionQueuedEventContext;
import javax.faces.event.PhaseId;
import javax.faces.event.SystemEvent;

import com.sun.faces.RIConstants;
import com.sun.faces.util.FacesLogger;


/**
 * <p>
 * A specialized implementation of {@link ExceptionHandler} for JSF 2.0 that
 * handles exceptions by writing error information to the 
 * partial response.
 * </p>
 *
 */
public class AjaxExceptionHandlerImpl extends ExceptionHandlerWrapper {

    private static final Logger LOGGER = FacesLogger.CONTEXT.getLogger();
    private static final String LOG_BEFORE_KEY =
          "jsf.context.exception.handler.log_before";
    private static final String LOG_AFTER_KEY =
          "jsf.context.exception.handler.log_after";
    private static final String LOG_KEY =
          "jsf.context.exception.handler.log";

    
    private LinkedList<ExceptionQueuedEvent> unhandledExceptions;
    private LinkedList<ExceptionQueuedEvent> handledExceptions;
    private ExceptionQueuedEvent handled;


    public AjaxExceptionHandlerImpl(ExceptionHandler handler) {
        super(handler);
    }

    /**
     * @return 
     * @see ExceptionHandler#getHandledExceptionQueuedEvent() 
     */
    @Override
    public ExceptionQueuedEvent getHandledExceptionQueuedEvent() {

        return handled;

    }

    /**
     * @see javax.faces.context.ExceptionHandlerWrapper#handle()
     */
    @Override
    public void handle() throws FacesException {

        for (Iterator<ExceptionQueuedEvent> i = getUnhandledExceptionQueuedEvents().iterator(); i.hasNext(); ) {
            ExceptionQueuedEvent event = i.next();
            ExceptionQueuedEventContext context = (ExceptionQueuedEventContext) event.getSource();
            try {
                Throwable t = context.getException();
                if (isRethrown(t)) {
                    handled = event;
                    Throwable unwrapped = getRootCause(t);

                    if (unwrapped != null) {
                        handlePartialResponseError(context.getContext(), unwrapped);
                    } else {
                        if (t instanceof FacesException) {
                            handlePartialResponseError(context.getContext(), t);
                        } else {
                            handlePartialResponseError(context.getContext(), 
                                    new FacesException(t.getMessage(), t));
                        }
                    }
                } else {
                    log(context);
                }

            } finally {
                if (handledExceptions == null) {
                    handledExceptions = new LinkedList<>();
                }
                handledExceptions.add(event);
                i.remove();
            }
        }
    }

    /**
     * @see javax.faces.context.ExceptionHandlerWrapper#processEvent(javax.faces.event.SystemEvent)
     */
    @Override
    public void processEvent(SystemEvent event) throws AbortProcessingException {

        if (event != null) {
            if (unhandledExceptions == null) {
                unhandledExceptions = new LinkedList<>();
            }
            unhandledExceptions.add((ExceptionQueuedEvent) event);
        }

    }

    /**
     * @see javax.faces.context.ExceptionHandlerWrapper#getUnhandledExceptionQueuedEvents()
     */
    @Override
    public Iterable<ExceptionQueuedEvent> getUnhandledExceptionQueuedEvents() {

        return ((unhandledExceptions != null)
                    ? unhandledExceptions
                    : Collections.<ExceptionQueuedEvent>emptyList());

    }


    /**
     * @see javax.faces.context.ExceptionHandlerWrapper#getHandledExceptionQueuedEvents()
     */
    @Override
    public Iterable<ExceptionQueuedEvent> getHandledExceptionQueuedEvents() {

        return ((handledExceptions != null)
                    ? handledExceptions
                    : Collections.<ExceptionQueuedEvent>emptyList());

    }



    // --------------------------------------------------------- Private Methods

     private void handlePartialResponseError(FacesContext context, Throwable t) {
         if (context.getResponseComplete()) {
             return; // don't write anything if the response is complete
         }
         try {

             ExternalContext extContext = context.getExternalContext();
             extContext.setResponseContentType(RIConstants.TEXT_XML_CONTENT_TYPE);
             extContext.addResponseHeader("Cache-Control", "no-cache");
             PartialResponseWriter writer = context.getPartialViewContext().getPartialResponseWriter();

             writer.startDocument();
             writer.startError(t.getClass().toString());
             String msg;
             if (context.isProjectStage(ProjectStage.Production)) {
                 msg = "See your server log for more information";
             } else {
                 if (t.getCause() != null) {
                     msg = t.getCause().getMessage();
                 } else {
                     msg = t.getMessage();
                 }
             }
             writer.write(((msg != null) ? msg : ""));
             writer.endError();
             writer.endDocument();
             
             if (LOGGER.isLoggable(Level.SEVERE)) {
                 StringWriter sw = new StringWriter();
                 PrintWriter pw = new PrintWriter(sw);
                 t.printStackTrace(pw);
                 LOGGER.log(Level.SEVERE, sw.toString());
             }
             
             context.responseComplete();
         } catch (IOException ioe) {
            if (LOGGER.isLoggable(Level.SEVERE)) {
                LOGGER.log(Level.SEVERE,
                           ioe.toString(),
                           ioe);
            }
         }
     }

    private boolean isRethrown(Throwable t) {

        return (!(t instanceof AbortProcessingException));

    }

    private void log(ExceptionQueuedEventContext exceptionContext) {

        UIComponent c = exceptionContext.getComponent();
        boolean beforePhase = exceptionContext.inBeforePhase();
        boolean afterPhase = exceptionContext.inAfterPhase();
        PhaseId phaseId = exceptionContext.getPhaseId();
        Throwable t = exceptionContext.getException();
        String key = getLoggingKey(beforePhase, afterPhase);
        if (LOGGER.isLoggable(Level.SEVERE)) {
            LOGGER.log(Level.SEVERE,
                       key,
                       new Object[] { t.getClass().getName(),
                                      phaseId.toString(),
                                      ((c != null) ? c.getClientId(exceptionContext.getContext()) : ""),
                                      t.getMessage()});
            LOGGER.log(Level.SEVERE, t.getMessage(), t);
        }

    }

    private String getLoggingKey(boolean beforePhase, boolean afterPhase) {
        if (beforePhase) {
            return LOG_BEFORE_KEY;
        } else if (afterPhase) {
            return LOG_AFTER_KEY;
        } else {
            return LOG_KEY;
        }
    }

}
