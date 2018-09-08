/*
 * Copyright (c) 2002-2018 Gargoyle Software Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.gargoylesoftware.htmlunit.javascript.host.xml;

import com.gargoylesoftware.htmlunit.BrowserRunner;
import com.gargoylesoftware.htmlunit.BrowserRunner.Alerts;
import com.gargoylesoftware.htmlunit.BrowserRunner.NotYetImplemented;
import com.gargoylesoftware.htmlunit.BrowserRunner.Tries;
import com.gargoylesoftware.htmlunit.HttpHeader;
import com.gargoylesoftware.htmlunit.WebDriverTestCase;
import com.gargoylesoftware.htmlunit.WebRequest;
import com.gargoylesoftware.htmlunit.html.HtmlPageTest;
import com.gargoylesoftware.htmlunit.util.NameValuePair;
import org.apache.commons.lang3.ArrayUtils;
import org.junit.ComparisonFailure;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Locale;

import static com.gargoylesoftware.htmlunit.BrowserRunner.TestedBrowser.*;
import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Tests for {@link XMLHttpRequest} as of <a href="https://xhr.spec.whatwg.org/">https://xhr.spec.whatwg.org/</a>.
 *
 * @author Eckard Mühlich
 */
@RunWith(BrowserRunner.class)
public class XMLHttpRequestWhatwgTest extends WebDriverTestCase {

    private static final String UNINITIALIZED = String.valueOf(XMLHttpRequest.UNSENT);
    private static final String LOADING = String.valueOf(XMLHttpRequest.OPENED);
    private static final String COMPLETED = String.valueOf(XMLHttpRequest.DONE);

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts({"1: 0-", "2: ", "3: 200-OK"})
    public void statusSync() throws Exception {
        final String html =
            "<html>\n"
            + "  <head>\n"
            + "    <title>XMLHttpRequest Test</title>\n"
            + "<script>\n"
            + "  var xhr = new XMLHttpRequest();\n"

            + "  alertStatus('1: ');\n"
            + "  xhr.open('GET', '/foo.xml', false);\n"
            + "  alert('2: ');\n"

            + "  xhr.send();\n"
            + "  alertStatus('3: ');\n"

            + "  function alertStatus(prefix) {\n"
            + "    var msg = prefix;\n"
            + "    try {\n"
            + "      msg = msg + xhr.status + '-';\n"
            + "    } catch(e) { msg = msg + 'ex: status' + '-' }\n"
            + "    try {\n"
            + "      msg = msg + xhr.statusText;;\n"
            + "    } catch(e) { msg = msg + 'ex: statusText' }\n"
            + "    alert(msg);\n"
            + "  }\n"
            + "</script>\n"
            + "  </head>\n"
            + "  <body></body>\n"
            + "</html>";

        getMockWebConnection().setDefaultResponse("<res></res>", "text/xml");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts(DEFAULT = {"1: 0-", "2: 0-", "#1: 0-", "3: 0-", "4: 0-", "#2: 200-OK", "#3: 200-OK", "#4: 200-OK"},
            IE = {"1: 0-", "2: 0-", "#1: 0-", "3: 0-", "#1: 0-", "4: 0-", "#2: 200-OK", "#3: 200-OK", "#4: 200-OK"})
    public void statusAsync() throws Exception {
        final String html =
            "<html>\n"
            + "  <head>\n"
            + "    <title>XMLHttpRequest Test</title>\n"
            + "<script>\n"
            + "  var xhr = new XMLHttpRequest();\n"

            + "  function test() {\n"
            + "    try {\n"
            + "      logStatus('1: ');\n"

            + "      xhr.onreadystatechange = onReadyStateChange;\n"
            + "      logStatus('2: ');\n"

            + "      xhr.open('GET', '/foo.xml', true);\n"
            + "      logStatus('3: ');\n"

            + "      xhr.send();\n"
            + "      logStatus('4: ');\n"
            + "    } catch(e) {\n"
            + "      document.getElementById('log').value += e + '\\n';\n"
            + "    }\n"
            + "  }\n"

            + "  function onReadyStateChange() {\n"
            + "    logStatus('#' + xhr.readyState + ': ');\n"
            + "  }\n"

            + "  function logStatus(prefix) {\n"
            + "    var msg = prefix;\n"
            + "    try {\n"
            + "      msg = msg + xhr.status + '-';\n"
            + "    } catch(e) { msg = msg + 'ex: status' + '-' }\n"
            + "    try {\n"
            + "      msg = msg + xhr.statusText;;\n"
            + "    } catch(e) { msg = msg + 'ex: statusText' }\n"
            + "    document.getElementById('log').value += msg + '\\n';\n"
            + "  }\n"
            + "</script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "    <textarea id='log' cols='80' rows='40'></textarea>\n"
            + "  </body>\n"
            + "</html>";

        getMockWebConnection().setDefaultResponse("<res></res>", "text/xml");
        final WebDriver driver = loadPage2(html);

        final String expected = String.join("\n", getExpectedAlerts());
        assertLog(driver, expected);
    }

    private void assertLog(final WebDriver driver, final String expected) throws InterruptedException {
        final long maxWait = System.currentTimeMillis() + DEFAULT_WAIT_TIME;
        while (true) {
            try {
                final String text = driver.findElement(By.id("log")).getAttribute("value").trim().replaceAll("\r", "");
                assertEquals(expected, text);
                return;
            }
            catch (final ComparisonFailure e) {
                if (System.currentTimeMillis() > maxWait) {
                    throw e;
                }
                Thread.sleep(10);
            }
        }
    }

    /**
     * Checks that not passing the async flag to <code>open()</code>
     * results in async execution.  If this gets interpreted as {@code false}
     * then you will see the alert order 1-2-4-3 instead of 1-2-3-4.
     * @throws Exception if the test fails
     */
    @Test
    @Alerts({"#1", "#2", "#3", "#4"})
    public void asyncIsDefault() throws Exception {
        final String html = "<html>\n"
            + "<body>\n"
            + "  <textarea id='log' cols='80' rows='40'></textarea>\n"

            + "<script>\n"
            + "    function log(x) {\n"
            + "      document.getElementById('log').value += x + '\\n';\n"
            + "    }\n"

            + "var xhr = new XMLHttpRequest();\n"

            + "function onReadyStateChange() {\n"
            + "  if( xhr.readyState == 4 ) {\n"
            + "    log('#4');\n"
            + "  }\n"
            + "}\n"

            + "try {\n"
            + "  log('#1');\n"
            + "  xhr.onreadystatechange = onReadyStateChange;\n"
            + "  xhr.open('GET', '/foo.xml');\n"
            + "  log('#2');\n"
            + "  xhr.send();\n"
            + "  log('#3');\n"
            + "} catch(e) { log(e); }\n"
            + "</script>\n"
            + "</body></html>";

        getMockWebConnection().setDefaultResponse("<res></res>", "text/xml");
        final WebDriver driver = loadPage2(html);

        final String expected = String.join("\n", getExpectedAlerts());
        assertLog(driver, expected);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts(DEFAULT = {"orsc1", "open-done", "send-done",
                "orsc2", "orsc3", "orsc4", "4", "<a>b</a>", "[object XMLHttpRequest]"},
            IE = {"orsc1", "open-done", "orsc1", "send-done",
                "orsc2", "orsc3", "orsc4", "4", "<a>b</a>", "[object XMLHttpRequest]"})
    public void onload() throws Exception {
        // TODO [IE]SINGLE-VS-BULK test runs when executed as single but breaks as bulk
        shutDownRealIE();

        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <script>\n"
            + "      function log(x) {\n"
            + "        document.getElementById('log').value += x + '\\n';\n"
            + "      }\n"

            + "      function test() {\n"
            + "        var xhr = new XMLHttpRequest();\n"

            + "        xhr.onreadystatechange = function() { log('orsc' + xhr.readyState); };\n"
            + "        xhr.onload = function() { log(xhr.readyState); log(xhr.responseText); log(this); }\n"

            + "        xhr.open('GET', '/foo.xml', true);\n"
            + "        log('open-done');\n"

            + "        xhr.send('');\n"
            + "        log('send-done');\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "    <textarea id='log' cols='80' rows='40'></textarea>\n"
            + "  </body>\n"
            + "</html>";

        final String xml = "<a>b</a>";

        getMockWebConnection().setDefaultResponse(xml, "text/xml");
        final WebDriver driver = loadPage2(html);

        final String expected = String.join("\n", getExpectedAlerts());
        assertLog(driver, expected);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts({"null", "null"})
    public void responseHeaderBeforeSend() throws Exception {
        final String html =
            "<html>\n"
            + "  <head>\n"
            + "    <title>XMLHttpRequest Test</title>\n"
            + "    <script>\n"
            + "        var request = new XMLHttpRequest();\n"

            + "        alert(request.getResponseHeader('content-length'));\n"
            + "        request.open('GET', '/foo.xml', false);\n"
            + "        alert(request.getResponseHeader('content-length'));\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body></body>\n"
            + "</html>";
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("")
    public void responseTypeDefaultEmpty() throws Exception {
        final String html = "<html><head>\n"
            + "<script>\n"
            + "function test() {\n"
            + "  var request = new XMLHttpRequest();\n"
            + "  request.open('GET', 'foo.txt', false);\n"
            + "  alert(request.responseType);\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("null")
    public void responseXML_text_html() throws Exception {
        // TODO [IE]SINGLE-VS-BULK test runs when executed as single but breaks as bulk
        shutDownRealIE();

        final String html = HtmlPageTest.STANDARDS_MODE_PREFIX_
            + "<html><head><title>foo</title><script>\n"
            + "  function test() {\n"
            + "    var xhr = new XMLHttpRequest();\n"
            + "    xhr.open('GET', 'foo.xml', false);\n"
            + "    xhr.send('');\n"
            + "    try {\n"
            + "      alert(xhr.responseXML);\n"
            + "    } catch(e) { alert('exception'); }\n"
            + "  }\n"
            + "</script></head><body onload='test()'>\n"
            + "</body></html>";

        getMockWebConnection().setDefaultResponse("<html></html>", "text/html");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("[object XMLDocument]")
    public void responseXML_text_xml() throws Exception {
        // TODO [IE]SINGLE-VS-BULK test runs when executed as single but breaks as bulk
        shutDownRealIE();

        final String html = HtmlPageTest.STANDARDS_MODE_PREFIX_
            + "<html><head><title>foo</title><script>\n"
            + "  function test() {\n"
            + "    var xhr = new XMLHttpRequest();\n"
            + "    xhr.open('GET', 'foo.xml', false);\n"
            + "    xhr.send('');\n"
            + "    try {\n"
            + "      alert(xhr.responseXML);\n"
            + "    } catch(e) { alert('exception'); }\n"
            + "  }\n"
            + "</script></head><body onload='test()'>\n"
            + "</body></html>";

        getMockWebConnection().setDefaultResponse("<note/>", "text/xml");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("[object XMLDocument]")
    public void responseXML_application_xml() throws Exception {
        // TODO [IE]SINGLE-VS-BULK test runs when executed as single but breaks as bulk
        shutDownRealIE();

        final String html = HtmlPageTest.STANDARDS_MODE_PREFIX_
            + "<html><head><title>foo</title><script>\n"
            + "  function test() {\n"
            + "    var xhr = new XMLHttpRequest();\n"
            + "    xhr.open('GET', 'foo.xml', false);\n"
            + "    xhr.send('');\n"
            + "    try {\n"
            + "      alert(xhr.responseXML);\n"
            + "    } catch(e) { alert('exception'); }\n"
            + "  }\n"
            + "</script></head><body onload='test()'>\n"
            + "</body></html>";

        getMockWebConnection().setDefaultResponse("<note/>", "application/xml");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("[object XMLDocument]")
    public void responseXML_application_xhtmlXml() throws Exception {
        // TODO [IE]SINGLE-VS-BULK test runs when executed as single but breaks as bulk
        shutDownRealIE();

        final String html = HtmlPageTest.STANDARDS_MODE_PREFIX_
            + "<html><head><title>foo</title><script>\n"
            + "  function test() {\n"
            + "    var xhr = new XMLHttpRequest();\n"
            + "    xhr.open('GET', 'foo.xml', false);\n"
            + "    xhr.send('');\n"
            + "    try {\n"
            + "      alert(xhr.responseXML);\n"
            + "    } catch(e) { alert('exception'); }\n"
            + "  }\n"
            + "</script></head><body onload='test()'>\n"
            + "</body></html>";

        getMockWebConnection().setDefaultResponse("<html/>", "application/xhtml+xml");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("[object XMLDocument]")
    public void responseXML_application_svgXml() throws Exception {
        // TODO [IE]SINGLE-VS-BULK test runs when executed as single but breaks as bulk
        shutDownRealIE();

        final String html = HtmlPageTest.STANDARDS_MODE_PREFIX_
            + "<html><head><title>foo</title><script>\n"
            + "  function test() {\n"
            + "    var xhr = new XMLHttpRequest();\n"
            + "    xhr.open('GET', 'foo.xml', false);\n"
            + "    xhr.send('');\n"
            + "    try {\n"
            + "      alert(xhr.responseXML);\n"
            + "    } catch(e) { alert('exception'); }\n"
            + "  }\n"
            + "</script></head><body onload='test()'>\n"
            + "</body></html>";

        getMockWebConnection().setDefaultResponse("<svg xmlns=\"http://www.w3.org/2000/svg\"/>", "image/svg+xml");
        loadPageWithAlerts2(html);
    }

    /**
     * Regression test for IE specific properties attribute.text & attribute.xml.
     * @throws Exception if the test fails
     */
    @Test
    @Alerts({"1", "someAttr", "undefined", "undefined"})
    public void responseXML2() throws Exception {
        // TODO [IE]SINGLE-VS-BULK test runs when executed as single but breaks as bulk
        shutDownRealIE();

        final String html = "<html><head>\n"
            + "<script>\n"
            + "function test() {\n"
            + "  var request = new XMLHttpRequest();\n"
            + "  request.open('GET', 'foo.xml', false);\n"
            + "  request.send('');\n"
            + "  var childNodes = request.responseXML.childNodes;\n"
            + "  alert(childNodes.length);\n"
            + "  var rootNode = childNodes[0];\n"
            + "  alert(rootNode.attributes[0].nodeName);\n"
            + "  alert(rootNode.attributes[0].text);\n"
            + "  alert(rootNode.attributes[0].xml);\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        final URL urlPage2 = new URL(URL_FIRST, "foo.xml");
        getMockWebConnection().setResponse(urlPage2,
            "<bla someAttr='someValue'><foo><fi id='fi1'/><fi/></foo></bla>\n",
            "text/xml");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("received: null")
    public void responseXML_siteNotExisting() throws Exception {
        final String html = "<html><head>\n"
            + "<script>\n"
            + "function test() {\n"
            + "  var request = new XMLHttpRequest();\n"
            + "try {\n"
            + "  request.open('GET', 'http://this.doesnt.exist/foo.xml', false);\n"
            + "  request.send('');\n"
            + "} catch(e) {\n"
            + "  alert('received: ' + request.responseXML);\n"
            + "}\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    public void sendNull() throws Exception {
        final String html = "<html><head>\n"
            + "<script>\n"
            + "function test() {\n"
            + "  var request = new XMLHttpRequest();\n"
            + "  request.open('GET', 'foo.txt', false);\n"
            + "  request.send(null);\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        getMockWebConnection().setDefaultResponse("");
        loadPageWithAlerts2(html);
    }

    /**
     * Test calls to send('foo') for a GET. HtmlUnit 1.14 was incorrectly throwing an exception.
     * @throws Exception if the test fails
     */
    @Test
    public void sendGETWithContent() throws Exception {
        send("'foo'");
    }

    /**
     * Test calls to send() without any arguments.
     * @throws Exception if the test fails
     */
    @Test
    public void sendNoArg() throws Exception {
        send("");
    }

    /**
     * @throws Exception if the test fails
     */
    private void send(final String sendArg) throws Exception {
        final String html = "<html><head>\n"
            + "<script>\n"
            + "function test() {\n"
            + "  var request = new XMLHttpRequest();\n"
            + "  request.open('GET', 'foo.txt', false);\n"
            + "  request.send(" + sendArg + ");\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        getMockWebConnection().setDefaultResponse("");
        loadPageWithAlerts2(html);
    }

    /**
     * Regression test for bug 1357412.
     * Response received by the XMLHttpRequest should not come in any window
     * @throws Exception if the test fails
     */
    @Test
    public void responseNotInWindow() throws Exception {
        final String html = "<html><head><title>foo</title>\n"
            + "<script>\n"
            + "function test() {\n"
            + "  var request = new XMLHttpRequest();\n"
            + "  request.open('GET', 'foo.txt', false);\n"
            + "  request.send();\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        getMockWebConnection().setDefaultResponse("");
        final WebDriver driver = loadPageWithAlerts2(html);
        assertEquals(URL_FIRST.toString(), driver.getCurrentUrl());
        assertTitle(driver, "foo");
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts({"true", "false"})
    public void overrideMimeType() throws Exception {
        final String html = "<html><head>\n"
            + "<script>\n"
            + "function test() {\n"
            + "try {\n"
            + "  var request = new XMLHttpRequest();\n"
            + "  request.open('GET', 'foo.xml.txt', false);\n"
            + "  request.send('');\n"
            + "  alert(request.responseXML == null);\n"
            + "  request.open('GET', 'foo.xml.txt', false);\n"
            + "  request.overrideMimeType('text/xml');\n"
            + "  request.send('');\n"
            + "  alert(request.responseXML == null);\n"
            + "} catch (e) { alert('exception'); }\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        final URL urlPage2 = new URL(URL_FIRST, "foo.xml.txt");
        getMockWebConnection().setResponse(urlPage2,
            "<bla someAttr='someValue'><foo><fi id='fi1'/><fi/></foo></bla>\n",
            "text/plain");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts({"true", "exception"})
    public void overrideMimeTypeAfterSend() throws Exception {
        final String html = "<html><head>\n"
            + "<script>\n"
            + "function test() {\n"
            + "  var request = new XMLHttpRequest();\n"
            + "  request.open('GET', 'foo.xml.txt', false);\n"
            + "  request.send('');\n"
            + "  alert(request.responseXML == null);\n"
            + "  try {\n"
            + "    request.overrideMimeType('text/xml');\n"
            + "    alert('overwritten');\n"
            + "  } catch (e) { alert('exception'); }\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        final URL urlPage2 = new URL(URL_FIRST, "foo.xml.txt");
        getMockWebConnection().setResponse(urlPage2,
            "<bla someAttr='someValue'><foo><fi id='fi1'/><fi/></foo></bla>\n",
            "text/plain");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("27035")
    public void overrideMimeType_charset() throws Exception {
        // TODO [IE]SINGLE-VS-BULK test runs when executed as single but breaks as bulk
        shutDownRealIE();

        final String html = "<html><head>\n"
            + "<script>\n"
            + "function test() {\n"
            + "try {\n"
            + "  var request = new XMLHttpRequest();\n"
            + "  request.open('GET', '" + URL_SECOND + "', false);\n"
            + "  request.overrideMimeType('text/plain; charset=GBK');\n"
            + "  request.send('');\n"
            + "  alert(request.responseText.charCodeAt(0));\n"
            + "} catch (e) { alert('exception'); }\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        getMockWebConnection().setResponse(URL_SECOND, "\u9EC4", "text/plain", UTF_8);
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("27035")
    public void overrideMimeType_charset_upper_case() throws Exception {
        // TODO [IE]SINGLE-VS-BULK test runs when executed as single but breaks as bulk
        shutDownRealIE();

        final String html = "<html><head>\n"
            + "<script>\n"
            + "function test() {\n"
            + "try {\n"
            + "  var request = new XMLHttpRequest();\n"
            + "  request.open('GET', '" + URL_SECOND + "', false);\n"
            + "  request.overrideMimeType('text/plain; chaRSet=GBK');\n"
            + "  request.send('');\n"
            + "  alert(request.responseText.charCodeAt(0));\n"
            + "} catch (e) { alert('exception'); }\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        getMockWebConnection().setResponse(URL_SECOND, "\u9EC4", "text/plain", UTF_8);
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("40644")
    public void overrideMimeType_charset_empty() throws Exception {
        // TODO [IE]SINGLE-VS-BULK test runs when executed as single but breaks as bulk
        shutDownRealIE();

        final String html = "<html><head>\n"
            + "<script>\n"
            + "function test() {\n"
            + "try {\n"
            + "  var request = new XMLHttpRequest();\n"
            + "  request.open('GET', '" + URL_SECOND + "', false);\n"
            + "  request.overrideMimeType('text/plain; charset=');\n"
            + "  request.send('');\n"
            + "  alert(request.responseText.charCodeAt(0));\n"
            + "} catch (e) { alert('exception'); }\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        getMockWebConnection().setResponse(URL_SECOND, "\u9EC4", "text/plain", UTF_8);
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts(DEFAULT = "40644",
            CHROME = {"233", "187", "8222"},
            IE = {})
    public void overrideMimeType_charset_wrong() throws Exception {
        // TODO [IE]SINGLE-VS-BULK test runs when executed as single but breaks as bulk
        shutDownRealIE();

        final String html = "<html><head>\n"
            + "<script>\n"
            + "function test() {\n"
            + "  try {\n"
            + "    var request = new XMLHttpRequest();\n"
            + "    request.open('GET', '" + URL_SECOND + "', false);\n"
            + "    request.overrideMimeType('text/plain; charset=abcdefg');\n"
            + "    request.send('');\n"
            + "    var text = request.responseText;\n"
            + "    for (var i = 0; i < text.length; i++) {\n"
            + "      alert(text.charCodeAt(i));\n"
            + "    }\n"
            + "  } catch (e) { alert('exception'); }\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        getMockWebConnection().setResponse(URL_SECOND, "\u9EC4", "text/plain", UTF_8);
        loadPageWithAlerts2(html);
    }

    /**
     * Tests that the <tt>Referer</tt> header is set correctly.
     * @throws Exception if the test fails
     */
    @Test
    public void refererHeader() throws Exception {
        final String html = "<html><head><script>\n"
            + "function test() {\n"
            + "  req = new XMLHttpRequest();\n"
            + "  req.open('post', 'foo.xml', false);\n"
            + "  req.send('');\n"
            + "}\n"
            + "</script></head>\n"
            + "<body onload='test()'></body></html>";

        final URL urlPage2 = new URL(URL_FIRST, "foo.xml");
        getMockWebConnection().setResponse(urlPage2, "<foo/>\n", "text/xml");
        loadPage2(html);

        final WebRequest request = getMockWebConnection().getLastWebRequest();
        assertEquals(urlPage2, request.getUrl());
        assertEquals(URL_FIRST.toExternalForm(), request.getAdditionalHeaders().get(HttpHeader.REFERER));
    }

    /**
     * Test for bug https://sourceforge.net/tracker/?func=detail&atid=448266&aid=1784330&group_id=47038.
     * @throws Exception if an error occurs
     */
    @Test
    @Alerts(DEFAULT = "ActiveXObject not available",
            IE = {"0", "0"})
    public void caseInsensitivityActiveXConstructor() throws Exception {
        final String html = "<html><head><script>\n"
            + "function test() {\n"
            + "  try {\n"
            + "    var req = new ActiveXObject('MSXML2.XmlHttp');\n"
            + "    alert(req.readyState);\n"

            + "    var req = new ActiveXObject('msxml2.xMLhTTp');\n"
            + "    alert(req.readyState);\n"
            + "  } catch (e) { alert('ActiveXObject not available'); }\n"
            + "}\n"
            + "</script></head>\n"
            + "<body onload='test()'></body></html>";

        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("selectNodes not available")
    public void responseXML_selectNodesIE() throws Exception {
        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <title>XMLHttpRequest Test</title>\n"
            + "    <script>\n"
            + "      function test() {\n"
            + "        var request = new XMLHttpRequest();\n"
            + "        request.open('GET', '" + URL_SECOND + "', false);\n"
            + "        request.send('');\n"
            + "        if (!request.responseXML.selectNodes) { alert('selectNodes not available'); return }\n"
            + "        alert(request.responseXML.selectNodes('//content').length);\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "  </body>\n"
            + "</html>";

        final String xml =
              "<xml>\n"
            + "<content>blah</content>\n"
            + "<content>blah2</content>\n"
            + "</xml>";

        getMockWebConnection().setResponse(URL_SECOND, xml, "text/xml");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts(DEFAULT = {"[object Element]", "myID", "blah", "span", "[object XMLDocument]"},
            IE = {"null", "myID", "blah", "span", "[object XMLDocument]"})
    public void responseXML_getElementById2() throws Exception {
        // TODO [IE]SINGLE-VS-BULK test runs when executed as single but breaks as bulk
        shutDownRealIE();

        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <title>XMLHttpRequest Test</title>\n"
            + "    <script>\n"
            + "      function test() {\n"
            + "        var request = new XMLHttpRequest();\n"
            + "        request.open('GET', '" + URL_SECOND + "', false);\n"
            + "        request.send('');\n"
            + "        if (request.responseXML.getElementById) {\n"
            + "          alert(request.responseXML.getElementById('id1'));\n"
            + "          alert(request.responseXML.getElementById('myID').id);\n"
            + "          alert(request.responseXML.getElementById('myID').innerHTML);\n"
            + "          alert(request.responseXML.getElementById('myID').tagName);\n"
            + "          alert(request.responseXML.getElementById('myID').ownerDocument);\n"
            + "        } else  {\n"
            + "          alert('responseXML.getElementById not available');\n"
            + "        }\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "  </body>\n"
            + "</html>";

        final String xml =
              "<xml>\n"
            + "<content id='id1'>blah</content>\n"
            + "<content>blah2</content>\n"
            + "<html xmlns='http://www.w3.org/1999/xhtml'>\n"
            + "<span id='myID'>blah</span>\n"
            + "<script src='foo.js'></script>\n"
            + "</html>\n"
            + "</xml>";

        getMockWebConnection().setResponse(URL_SECOND, xml, "text/xml");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts({"[object Element]", "[object Element]", "[object HTMLBodyElement]",
                "[object HTMLSpanElement]", "[object XMLDocument]", "undefined"})
    public void responseXML_getElementById() throws Exception {
        // TODO [IE]SINGLE-VS-BULK test runs when executed as single but breaks as bulk
        shutDownRealIE();

        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <title>XMLHttpRequest Test</title>\n"
            + "    <script>\n"
            + "      function test() {\n"
            + "        var request = new XMLHttpRequest();\n"
            + "        request.open('GET', '" + URL_SECOND + "', false);\n"
            + "        request.send('');\n"
            + "        var doc = request.responseXML;\n"
            + "        alert(doc.documentElement);\n"
            + "        alert(doc.documentElement.childNodes[0]);\n"
            + "        alert(doc.documentElement.childNodes[1]);\n"
            + "        if (doc.getElementById) {\n"
            + "          alert(doc.getElementById('out'));\n"
            + "          alert(doc.getElementById('out').ownerDocument);\n"
            + "        }\n"
            + "        alert(doc.documentElement.childNodes[1].xml);\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "  </body>\n"
            + "</html>";

        final String xml =
              "<html>"
            + "<head>"
            + "</head>"
            + "<body xmlns='http://www.w3.org/1999/xhtml'>"
            + "<span id='out'>Hello Bob Dole!</span>"
            + "</body>"
            + "</html>";

        getMockWebConnection().setResponse(URL_SECOND, xml, "text/xml");
        loadPageWithAlerts2(html);
    }

    /**
     * Verifies that the default encoding for an XMLHttpRequest is UTF-8.
     * @throws Exception if an error occurs
     */
    @Test
    @Alerts("ol\u00E9")
    public void defaultEncodingIsUTF8() throws Exception {
        // TODO [IE]SINGLE-VS-BULK test runs when executed as single but breaks as bulk
        shutDownRealIE();

        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <script>\n"
            + "      function test() {\n"
            + "        var request = new XMLHttpRequest();\n"
            + "        request.open('GET', '" + URL_SECOND + "', false);\n"
            + "        request.send('');\n"
            + "        alert(request.responseText);\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "  </body>\n"
            + "</html>";

        final String response = "ol\u00E9";
        final byte[] responseBytes = response.getBytes(UTF_8);

        getMockWebConnection().setResponse(URL_SECOND, responseBytes, 200, "OK", "text/html",
            new ArrayList<NameValuePair>());
        loadPageWithAlerts2(html);
    }

    /**
     * Custom servlet which streams content to the client little by little.
     */
    public static final class StreamingServlet extends HttpServlet {
        /** {@inheritDoc} */
        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
            resp.setStatus(200);
            resp.addHeader(HttpHeader.CONTENT_TYPE, "text/html");
            try {
                for (int i = 0; i < 10; i++) {
                    resp.getOutputStream().print(String.valueOf(i));
                    resp.flushBuffer();
                    Thread.sleep(150);
                }
            }
            catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
    }

    /**
     * Servlet for testing XMLHttpRequest basic authentication.
     */
    public static final class BasicAuthenticationServlet extends HttpServlet {

        /**
         * {@inheritDoc}
         */
        @Override
        protected void doPost(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
            handleRequest(req, resp);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        protected void doGet(final HttpServletRequest req, final HttpServletResponse resp) throws IOException {
            handleRequest(req, resp);
        }

        private static void handleRequest(final HttpServletRequest req, final HttpServletResponse resp)
                    throws IOException {
            final String authHdr = req.getHeader("Authorization");
            if (null == authHdr) {
                resp.setStatus(401);
                resp.setHeader("WWW-Authenticate", "Basic realm=\"someRealm\"");
            }
            else {
                final String[] authHdrTokens = authHdr.split("\\s+");
                String authToken = "";
                if (authHdrTokens.length == 2) {
                    authToken += authHdrTokens[0] + ':' + authHdrTokens[1];
                }

                resp.setStatus(200);
                resp.addHeader(HttpHeader.CONTENT_TYPE, "text/plain");
                resp.getOutputStream().print(authToken);
                resp.flushBuffer();
            }
        }
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("myID")
    public void responseXML_html_select() throws Exception {
        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <title>XMLHttpRequest Test</title>\n"
            + "    <script>\n"
            + "      function test() {\n"
            + "        try {\n"
            + "          var request = new XMLHttpRequest();\n"
            + "          request.open('GET', '" + URL_SECOND + "', false);\n"
            + "          request.send('');\n"
            + "          alert(request.responseXML.getElementById('myID').id);\n"
            + "        } catch (e) { alert('exception'); }\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "  </body>\n"
            + "</html>";

        final String xml =
              "<xml>\n"
            + "<content id='id1'>blah</content>\n"
            + "<content>blah2</content>\n"
            + "<html xmlns='http://www.w3.org/1999/xhtml'>\n"
            + "<select id='myID'><option>One</option></select>\n"
            + "</html>\n"
            + "</xml>";

        getMockWebConnection().setResponse(URL_SECOND, xml, "text/xml");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("myInput")
    public void responseXML_html_form() throws Exception {
        // TODO [IE]SINGLE-VS-BULK test runs when executed as single but breaks as bulk
        shutDownRealIE();

        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <title>XMLHttpRequest Test</title>\n"
            + "    <script>\n"
            + "      function test() {\n"
            + "        try {\n"
            + "          var request = new XMLHttpRequest();\n"
            + "          request.open('GET', '" + URL_SECOND + "', false);\n"
            + "          request.send('');\n"
            + "          alert(request.responseXML.getElementById('myID').myInput.name);\n"
            + "        } catch (e) { alert('exception'); }\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "  </body>\n"
            + "</html>";

        final String xml =
              "<xml>\n"
            + "<content id='id1'>blah</content>\n"
            + "<content>blah2</content>\n"
            + "<html xmlns='http://www.w3.org/1999/xhtml'>\n"
            + "<form id='myID'><input name='myInput'/></form>\n"
            + "</html>\n"
            + "</xml>";

        getMockWebConnection().setResponse(URL_SECOND, xml, "text/xml");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if an error occurs
     */
    @Test
    @Alerts(DEFAULT = "ActiveXObject not available",
            IE = {"0", "0"})
    public void caseSensitivity_activeX() throws Exception {
        final String html = "<html><head><script>\n"
            + "function test() {\n"
            + "  try {\n"
            + "    var req = new ActiveXObject('MSXML2.XmlHttp');\n"
            + "    alert(req.readyState);\n"
            + "    alert(req.reAdYsTaTe);\n"
            + "  } catch (e) { alert('ActiveXObject not available'); }\n"
            + "}\n"
            + "</script></head>\n"
            + "<body onload='test()'></body></html>";
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if an error occurs
     */
    @Test
    @Alerts({"0", "undefined"})
    public void caseSensitivity_XMLHttpRequest() throws Exception {
        final String html = "<html><head><script>\n"
            + "function test() {\n"
            + "  try {\n"
            + "    var req = new XMLHttpRequest();\n"
            + "    alert(req.readyState);\n"
            + "    alert(req.reAdYsTaTe);\n"
            + "  } catch (e) { alert('exception'); }\n"
            + "}\n"
            + "</script></head>\n"
            + "<body onload='test()'></body></html>";
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    public void isAuthorizedHeader() throws Exception {
        assertTrue(XMLHttpRequest.isAuthorizedHeader("Foo"));
        assertTrue(XMLHttpRequest.isAuthorizedHeader(HttpHeader.CONTENT_TYPE));

        final String[] headers = {"accept-charset", HttpHeader.ACCEPT_ENCODING_LC,
            HttpHeader.CONNECTION_LC, HttpHeader.CONTENT_LENGTH_LC, HttpHeader.COOKIE_LC, "cookie2",
            "content-transfer-encoding", "date",
            "expect", HttpHeader.HOST_LC, "keep-alive", HttpHeader.REFERER_LC,
            "te", "trailer", "transfer-encoding", "upgrade",
            HttpHeader.USER_AGENT_LC, "via" };
        for (final String header : headers) {
            assertFalse(XMLHttpRequest.isAuthorizedHeader(header));
            assertFalse(XMLHttpRequest.isAuthorizedHeader(header.toUpperCase(Locale.ROOT)));
        }
        assertFalse(XMLHttpRequest.isAuthorizedHeader("Proxy-"));
        assertFalse(XMLHttpRequest.isAuthorizedHeader("Proxy-Control"));
        assertFalse(XMLHttpRequest.isAuthorizedHeader("Proxy-Hack"));
        assertFalse(XMLHttpRequest.isAuthorizedHeader("Sec-"));
        assertFalse(XMLHttpRequest.isAuthorizedHeader("Sec-Hack"));
    }

    /**
     * Test case for Bug #1623.
     *
     * @throws Exception if the test fails
     */
    @Test
    @Alerts(DEFAULT = {"39", "27035", "65533", "39"},
            IE = {"39", "27035", "63"})
    @NotYetImplemented(IE)
    public void overrideMimeType_charset_all() throws Exception {
        // TODO [IE]SINGLE-VS-BULK test runs when executed as single but breaks as bulk
        shutDownRealIE();

        final String html = "<html><head>\n"
            + "<script>\n"
            + "function test() {\n"
            + "try {\n"
            + "  var request = new XMLHttpRequest();\n"
            + "  request.open('GET', '" + URL_SECOND + "', false);\n"
            + "  request.overrideMimeType('text/plain; charset=GBK');\n"
            + "  request.send('');\n"
            + "  for (var i = 0; i < request.responseText.length; i++) {\n"
            + "    alert(request.responseText.charCodeAt(i));\n"
            + "  }\n"
            + "} catch (e) { alert('exception'); }\n"
            + "}\n"
            + "</script>\n"
            + "</head>\n"
            + "<body onload='test()'></body></html>";

        getMockWebConnection().setResponse(URL_SECOND, "'\u9EC4'", "text/plain", UTF_8);
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    public void java_encoding() throws Exception {
        // Chrome and FF return the last apostrophe, see overrideMimeType_charset_all()
        // but Java and other tools (e.g. Notpad++) return only 3 characters, not 4
        // this method is not a test case, but rather to show the behavior of java

        final String string = "'\u9EC4'";
        final ByteArrayInputStream bais = new ByteArrayInputStream(string.getBytes(UTF_8));
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(bais, "GBK"))) {
            final String output = reader.readLine();
            assertNotNull(output);
            assertEquals(39, output.codePointAt(0));
            assertEquals(27035, output.codePointAt(1));
            assertEquals(65533, output.codePointAt(2));
            assertEquals(39, output.codePointAt(3));
        }
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts("[object ProgressEvent]")
    public void loadParameter() throws Exception {
        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <script>\n"
            + "      function someLoad(e) {\n"
            + "        alert(e);\n"
            + "      }\n"
            + "      function test() {\n"
            + "        try {\n"
            + "          var request = new XMLHttpRequest();\n"
            + "          request.onload = someLoad;\n"
            + "          request.open('GET', '" + URL_SECOND + "', false);\n"
            + "          request.send('');\n"
            + "        } catch (e) { alert('exception'); }\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "  </body>\n"
            + "</html>";

        final String xml = "<abc></abc>";

        getMockWebConnection().setResponse(URL_SECOND, xml, "text/xml");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts(DEFAULT = {"someLoad [object ProgressEvent]", "load", "false"},
            IE = {"someLoad [object ProgressEvent]", "load", "true"})
    public void addEventListener() throws Exception {
        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <script>\n"
            + "      function someLoad(event) {\n"
            + "        alert('someLoad ' + event);\n"
            + "        alert(event.type);\n"
            + "        alert(event.lengthComputable);\n"
            + "      }\n"
            + "      function test() {\n"
            + "        try {\n"
            + "          var request = new XMLHttpRequest();\n"
            + "          request.addEventListener('load', someLoad, false);\n"
            + "          request.open('GET', '" + URL_SECOND + "', false);\n"
            + "          request.send('');\n"
            + "        } catch (e) { alert('exception'); }\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "  </body>\n"
            + "</html>";

        final String xml = "<abc></abc>";

        getMockWebConnection().setResponse(URL_SECOND, xml, "text/xml");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts(DEFAULT = {"someLoad [object ProgressEvent]", "load", "false", "11", "0"},
            IE = {"someLoad [object ProgressEvent]", "load", "true", "11", "11"})
    public void addEventListenerDetails() throws Exception {
        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <script>\n"
            + "      function someLoad(event) {\n"
            + "        alert('someLoad ' + event);\n"
            + "        alert(event.type);\n"
            + "        alert(event.lengthComputable);\n"
            + "        alert(event.loaded);\n"
            + "        alert(event.total);\n"
            + "      }\n"
            + "      function test() {\n"
            + "        try {\n"
            + "          var request = new XMLHttpRequest();\n"
            + "          request.addEventListener('load', someLoad, false);\n"
            + "          request.open('GET', '" + URL_SECOND + "', false);\n"
            + "          request.send('');\n"
            + "        } catch (e) { alert('exception'); }\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "  </body>\n"
            + "</html>";

        final String xml = "<abc></abc>";

        getMockWebConnection().setResponse(URL_SECOND, xml, "text/xml");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts(DEFAULT = "function",
            IE = "null")
    @NotYetImplemented
    public void addEventListenerCaller() throws Exception {
        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <script>\n"
            + "      function someLoad(event) {\n"
            + "        var caller = arguments.callee.caller;\n"
            + "        alert(typeof caller == 'function' ? 'function' : caller);\n"
            + "      }\n"
            + "      function test() {\n"
            + "        try {\n"
            + "          var request = new XMLHttpRequest();\n"
            + "          request.addEventListener('load', someLoad, false);\n"
            + "          request.open('GET', '" + URL_SECOND + "', false);\n"
            + "          request.send('');\n"
            + "        } catch (e) { alert('exception'); }\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "  </body>\n"
            + "</html>";

        final String xml = "<abc></abc>";

        getMockWebConnection().setResponse(URL_SECOND, xml, "text/xml");
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts(DEFAULT = "[object XMLHttpRequestUpload]",
            IE = "[object XMLHttpRequestEventTarget]")
    public void upload() throws Exception {
        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <script>\n"
            + "      function test() {\n"
            + "        try {\n"
            + "          var request = new XMLHttpRequest();\n"
            + "          alert(request.upload);\n"
            + "        } catch (e) { alert('exception'); }\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "  </body>\n"
            + "</html>";

        loadPageWithAlerts2(html);
    }

    /**
     * Tests asynchronous use of XMLHttpRequest, using Mozilla style object creation.
     * @throws Exception if the test fails
     */
    @Test
    @Alerts(DEFAULT = {"0", "1", "2", "3", "4"},
            IE = {"0", "1", "1", "2", "3", "4"})
    public void asyncUse() throws Exception {
        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <title>XMLHttpRequest Test</title>\n"
            + "    <script>\n"
            + "      var request;\n"
            + "      function testAsync() {\n"
            + "        request = new XMLHttpRequest();\n"
            + "        request.onreadystatechange = onReadyStateChange;\n"
            + "        alert(request.readyState);\n"
            + "        request.open('GET', '" + URL_SECOND + "', true);\n"
            + "        request.send('');\n"
            + "      }\n"
            + "      function onReadyStateChange() {\n"
            + "        alert(request.readyState);\n"
            + "        if (request.readyState == 4)\n"
            + "          alert(request.responseText);\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='testAsync()'>\n"
            + "  </body>\n"
            + "</html>";

        final String xml =
              "<xml2>\n"
            + "<content2>sdgxsdgx</content2>\n"
            + "<content2>sdgxsdgx2</content2>\n"
            + "</xml2>";

        getMockWebConnection().setResponse(URL_SECOND, xml, "text/xml");
        setExpectedAlerts(ArrayUtils.add(getExpectedAlerts(), xml));
        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts(FF = {"[object Object]", "undefined", "undefined",
                        "function get onreadystatechange() {\n    [native code]\n}",
                        "function set onreadystatechange() {\n    [native code]\n}",
                        "true", "true"},
            CHROME = {"[object Object]", "undefined", "undefined",
                        "function () { [native code] }",
                        "function () { [native code] }",
                        "true", "true"},
            IE = {"[object Object]", "undefined", "undefined",
                    "\nfunction onreadystatechange() {\n    [native code]\n}\n",
                    "\nfunction onreadystatechange() {\n    [native code]\n}\n",
                    "true", "true"})
    @NotYetImplemented({CHROME, FF})
    public void getOwnPropertyDescriptor() throws Exception {
        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <title>XMLHttpRequest Test</title>\n"
            + "    <script>\n"
            + "      var request;\n"
            + "      function test() {\n"
            + "        var desc = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'onreadystatechange');\n"
            + "        alert(desc);\n"
            + "        alert(desc.value);\n"
            + "        alert(desc.writable);\n"
            + "        alert(desc.get);\n"
            + "        alert(desc.set);\n"
            + "        alert(desc.configurable);\n"
            + "        alert(desc.enumerable);\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "  </body>\n"
            + "</html>";

        loadPageWithAlerts2(html);
    }

    /**
     * @throws Exception if the test fails
     */
    @Test
    @Alerts(FF52 = {"[object Object]", "undefined", "undefined",
                        "function () { return !0 }",
                        "function set onreadystatechange() {\n    [native code]\n}",
                        "true", "true"},
            FF60 = {"[object Object]", "undefined", "undefined",
                        "function() { return !0 }",
                        "function set onreadystatechange() {\n    [native code]\n}",
                        "true", "true"},
            CHROME = {"[object Object]", "undefined", "undefined",
                        "function() { return !0 }",
                        "function () { [native code] }",
                        "true", "true"},
            IE = {"[object Object]", "undefined", "undefined",
                    "function() { return !0 }",
                    "\nfunction onreadystatechange() {\n    [native code]\n}\n",
                    "true", "true"})
    @NotYetImplemented
    public void defineProperty() throws Exception {
        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <title>XMLHttpRequest Test</title>\n"
            + "    <script>\n"
            + "      var request;\n"
            + "      function test() {\n"
            + "        Object.defineProperty(XMLHttpRequest.prototype, 'onreadystatechange', {\n"
            + "                                 enumerable: !0,\n"
            + "                                 configurable: !0,\n"
            + "                                 get: function() { return !0 }\n"
            + "                             });\n"
            + "        var desc = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'onreadystatechange');\n"
            + "        alert(desc);\n"
            + "        alert(desc.value);\n"
            + "        alert(desc.writable);\n"
            + "        alert(desc.get);\n"
            + "        alert(desc.set);\n"
            + "        alert(desc.configurable);\n"
            + "        alert(desc.enumerable);\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "  </body>\n"
            + "</html>";

        loadPageWithAlerts2(html);
    }

    /**
     * Test case for https://stackoverflow.com/questions/44349339/htmlunit-ecmaerror-typeerror.
     *
     * @throws Exception if the test fails
     */
    @Test
    @Alerts(DEFAULT = "[object XMLHttpRequestPrototype]",
            CHROME = "[object XMLHttpRequest]")
    @NotYetImplemented({FF, IE})
    public void defineProperty2() throws Exception {
        final String html =
              "<html>\n"
            + "  <head>\n"
            + "    <title>XMLHttpRequest Test</title>\n"
            + "    <script>\n"
            + "      var request;\n"
            + "      function test() {\n"
            + "        var t = Object.getOwnPropertyDescriptor(XMLHttpRequest.prototype, 'onreadystatechange');\n"
            + "        var res = Object.defineProperty(XMLHttpRequest.prototype, 'onreadystatechange', t);\n"
            + "        alert(res);\n"
            + "      }\n"
            + "    </script>\n"
            + "  </head>\n"
            + "  <body onload='test()'>\n"
            + "  </body>\n"
            + "</html>";

        loadPageWithAlerts2(html);
    }
}
