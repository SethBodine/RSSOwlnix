/*   **********************************************************************  **
 **   Copyright notice                                                       **
 **                                                                          **
 **   (c) 2005-2009 RSSOwl Development Team                                  **
 **   http://www.rssowl.org/                                                 **
 **                                                                          **
 **   All rights reserved                                                    **
 **                                                                          **
 **   This program and the accompanying materials are made available under   **
 **   the terms of the Eclipse Public License v1.0 which accompanies this    **
 **   distribution, and is available at:                                     **
 **   http://www.rssowl.org/legal/epl-v10.html                               **
 **                                                                          **
 **   A copy is found in the file epl-v10.html and important notices to the  **
 **   license from the team is found in the textfile LICENSE.txt distributed **
 **   in this package.                                                       **
 **                                                                          **
 **   This copyright notice MUST APPEAR in all copies of the file!           **
 **                                                                          **
 **   Contributors:                                                          **
 **     RSSOwl Development Team - initial API and implementation             **
 **                                                                          **
 **  **********************************************************************  */

package org.rssowl.core.internal.connection;

import org.apache.hc.client5.http.socket.ConnectionSocketFactory;
import org.apache.hc.client5.http.socket.LayeredConnectionSocketFactory;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.apache.hc.core5.util.TimeValue;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;

/**
 * Wraps a {@link ConnectionSocketFactory} (plain HTTP or layered/SSL) to
 * force the underlying {@link Socket} to be created via
 * {@code new Socket(Proxy.NO_PROXY)} instead of the bare {@code new Socket()}
 * that {@code PlainConnectionSocketFactory} and Apache's
 * {@code SSLConnectionSocketFactory} use internally.
 * <p>
 * The JDK transparently routes any socket created with the bare constructor
 * through the {@code socksProxyHost}/{@code socksProxyPort} system
 * properties whenever they happen to be set - entirely independent of, and
 * unaffected by, whatever HttpClient's own Route Planner decided. Eclipse's
 * {@code org.eclipse.core.net} bundle sets those system properties JVM-wide
 * to mirror a manually configured SOCKS proxy, so a "direct" connection that
 * HttpClient's routing correctly resolved to "no Proxy" could still be
 * silently tunneled through the SOCKS proxy one layer deeper, at the raw
 * socket layer.
 * <p>
 * {@code Proxy.NO_PROXY} is the JDK-documented way to opt a single
 * {@link Socket} out of that implicit JVM-wide proxying. This class only
 * replaces socket <em>creation</em>; the actual connect/TLS-handshake logic
 * is delegated to the wrapped factory unchanged.
 * <p>
 * Only registered in place of the normal Socket Factories when a Folder
 * Proxy-bypass override is active for the current request; otherwise the
 * normal Socket Factories are used so that a real Proxy - including a
 * configured SOCKS Proxy - continues to work as expected.
 */
public class DirectConnectionSocketFactory implements LayeredConnectionSocketFactory {

  private final ConnectionSocketFactory fDelegate;

  /**
   * @param delegate the Socket Factory to delegate the actual
   * connect/handshake logic to. May optionally also implement
   * {@link LayeredConnectionSocketFactory} (e.g. for SSL/TLS); if it does
   * not, {@link #createLayeredSocket} is a no-op passthrough.
   */
  public DirectConnectionSocketFactory(ConnectionSocketFactory delegate) {
    fDelegate = delegate;
  }

  @Override
  public Socket createSocket(HttpContext context) throws IOException {
    return new Socket(Proxy.NO_PROXY);
  }

  @Override
  public Socket connectSocket(TimeValue connectTimeout, Socket socket, HttpHost host, InetSocketAddress remoteAddress, InetSocketAddress localAddress, HttpContext context) throws IOException {
    return fDelegate.connectSocket(connectTimeout, socket, host, remoteAddress, localAddress, context);
  }

  @Override
  public Socket createLayeredSocket(Socket socket, String target, int port, HttpContext context) throws IOException {
    if (fDelegate instanceof LayeredConnectionSocketFactory)
      return ((LayeredConnectionSocketFactory) fDelegate).createLayeredSocket(socket, target, port, context);
    return socket;
  }
}
