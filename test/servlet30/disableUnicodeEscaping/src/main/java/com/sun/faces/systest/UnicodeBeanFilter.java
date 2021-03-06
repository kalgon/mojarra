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

package com.sun.faces.systest;

import javax.servlet.Filter;
import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

import com.sun.faces.config.WebConfiguration;

/**
 * a filter which sets the character encoding of the request and response to the one set by the browsers
 * Accept-Encoding header.
 * This is mainly usful for the UnicodeTestCase where we have to check the response with different encodings.
 */
public class UnicodeBeanFilter implements Filter {

    private ServletContext ctx;
    public void init(FilterConfig filterConfig) throws ServletException {
        ctx = filterConfig.getServletContext();
    }

    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        if (servletRequest instanceof HttpServletRequest) {
            HttpServletRequest hreq = (HttpServletRequest) servletRequest;
            String charsetEncoding = hreq.getHeader("Accept-Encoding");
            
            servletRequest.setCharacterEncoding(charsetEncoding);
            servletResponse.setCharacterEncoding(charsetEncoding);
            WebConfiguration config = WebConfiguration.getInstance(ctx);            
            config.overrideContextInitParameter(WebConfiguration.WebContextInitParameter.DisableUnicodeEscaping, servletRequest.getParameter("escape"));
        }
        filterChain.doFilter(servletRequest, servletResponse);
    }

    public void destroy() {
    }
}
