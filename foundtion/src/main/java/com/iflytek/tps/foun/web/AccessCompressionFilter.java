package com.iflytek.tps.foun.web;

import com.google.common.collect.Maps;
import com.iflytek.tps.foun.dto.AccessLog;
import com.iflytek.tps.foun.dto.DateFormat;
import com.iflytek.tps.foun.helper.CaptureHelper;
import com.iflytek.tps.foun.util.CollectionUtils;
import com.iflytek.tps.foun.util.DateUtils;
import com.iflytek.tps.foun.util.MatcherUtils;
import com.iflytek.tps.foun.util.NetworkUtils;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.servlet.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpServletResponseWrapper;
import java.io.*;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.zip.GZIPOutputStream;

/** 访问日志处理以及特殊 URI Response 大于 4K 的做压缩处理 **/
public class AccessCompressionFilter implements Filter {
    protected static final Logger LOG = LoggerFactory.getLogger(AccessCompressionFilter.class.getSimpleName());
    private static final String ACCEPT_ENCODING = "Accept-Encoding";
    private static final String CONTENT_ENCODING = "content-encoding";
    private static final String X_ACCESS_TIME = "X-Access-Time";
    private static final String X_REPLY_TIME = "X-Reply-Time";
    private static final String GZIP = "gzip";
    private static final Long NEED_COM_SIZE = 4 * 1024L;
    private final Set<String> gzipUriSet;

    public AccessCompressionFilter(Set<String> gzipUriSet) {
        //设置日志不向上层传播
        ((ch.qos.logback.classic.Logger) LOG).setAdditive(false);

        this.gzipUriSet = gzipUriSet;
        // hit on cache
        MatcherUtils.hitAntPathMatcherCache(this.gzipUriSet);
    }

    public void init(FilterConfig config) throws ServletException {
        LOG.trace("compression filter init......");
    }

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain) throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest)req;
        HttpServletResponse httpResponse = (HttpServletResponse) res;
        httpResponse.setHeader(X_ACCESS_TIME, String.valueOf(DateUtils.time()));
        //生成 访问 LOG
        AccessLog log = createAccessLog(httpRequest);
        RequestWrapper request = new RequestWrapper(httpRequest, log.bodyBuf);
        ResponseWrapper response = new ResponseWrapper(httpResponse);
        chain.doFilter(request, response);
        response.finish();
        log.responseHeaders = headers(Collections.enumeration(response.getHeaderNames()), s -> response.getHeader(s));
        log.responseBuf = response.body();
        log.responseTime = DateUtils.now(DateFormat.NumDateTime);
        if(!res.isCommitted()) {
            ServletOutputStream sos = res.getOutputStream();
            ByteArrayOutputStream bos = null;
            GZIPOutputStream gzip = null;
            try {
                //如果 Client 支持则压缩
                if (needCompress(httpRequest, log.responseBuf)) {
                    httpResponse.addHeader(CONTENT_ENCODING, GZIP);
                    bos = new ByteArrayOutputStream();
                    gzip = new GZIPOutputStream(bos);
                    gzip.write(log.responseBuf);
                    gzip.finish();
                    bos.flush();
                    byte[] buffer = bos.toByteArray();
                    response.setContentLength(buffer.length);
                    response.setHeader(X_REPLY_TIME, String.valueOf(DateUtils.time()));
                    sos.write(buffer);
                } else {
                    response.setContentLength(log.responseBuf.length);
                    response.setHeader(X_REPLY_TIME, String.valueOf(DateUtils.time()));
                    sos.write(log.responseBuf);
                }
                sos.flush();
            } finally {
                IOUtils.closeQuietly(bos);
                IOUtils.closeQuietly(gzip);
                IOUtils.closeQuietly(sos);
            }
        }
        CaptureHelper.async(() -> LOG.info(log.toString()), null);
    }

    public void destroy() {
        LOG.trace("compression filter destroy......");
    }

    @NotNull
    private AccessLog createAccessLog(HttpServletRequest request) throws IOException {
        AccessLog log = new AccessLog();
        log.requestTime = DateUtils.now(DateFormat.NumDateTime);
        log.domain = request.getServerName();
        log.uri = request.getRequestURI();
        log.method = request.getMethod();
        log.protocol = request.getProtocol();
        log.requestHeaders = headers(request.getHeaderNames(), s -> request.getHeader(s));
        log.params = request.getQueryString();
        log.bodyBuf = IOUtils.toByteArray(request.getInputStream());
        log.clientIp = NetworkUtils.ofClientIp(request);
        log.serverIp = NetworkUtils.ofInnerIp();
        return log;
    }

    private Map<String, String> headers(Enumeration<String> headers, Function<String, String> fun) {
        Map<String, String> hMap = Maps.newHashMap();
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            hMap.put(header, fun.apply(header));
        }
        return hMap;
    }

    /** 判断请求返回体是需要压缩 **/
    private boolean needCompress(HttpServletRequest request, byte[] src) {
        boolean compress = src.length >= NEED_COM_SIZE && supportGzip(request);
        //当没有特殊配置 gzipUriSet 对拦截到的所有符合压缩条件的请求都做压缩处理
        if(CollectionUtils.isNullOrEmpty(gzipUriSet)){
            return compress;
        }else {
            return MatcherUtils.isPathMatch(request.getRequestURI(), gzipUriSet) && compress;
        }
    }

    /** 判断请求是否支持 GZIP **/
    private boolean supportGzip(HttpServletRequest request) {
        Enumeration headers = request.getHeaders(ACCEPT_ENCODING);
        while (headers.hasMoreElements()) {
            String value = (String) headers.nextElement();
            if (value.contains(GZIP)) {
                return true;
            }
        }
        return false;
    }

    private final class RequestWrapper extends HttpServletRequestWrapper {
        private final byte[] body;
        /**
         * Constructs a request object wrapping the given request.
         *
         * @param request
         * @throws IllegalArgumentException if the request is null
         */
        public RequestWrapper(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override public ServletInputStream getInputStream() throws IOException {
            return new ServletInputStream() {
                @Override public boolean isFinished() {
                    return false;
                }

                @Override public boolean isReady() {
                    return true;
                }

                @Override public void setReadListener(ReadListener readListener) {

                }
                final ByteArrayInputStream bis = new ByteArrayInputStream(body);
                @Override public int read() throws IOException {
                    return bis.read();
                }
            };
        }
    }

    private final class ResponseWrapper extends HttpServletResponseWrapper {
        public static final int OT_NONE = 0, OT_WRITER = 1, OT_STREAM = 2;
        private int outputType = OT_NONE;
        private ByteArrayOutputStream buffer = null;
        private ServletOutputStream output = null;
        private PrintWriter writer = null;

        /**
         * Constructs a response adaptor wrapping the given response.
         *
         * @param response
         * @throws IllegalArgumentException if the response is null
         */
        public ResponseWrapper(HttpServletResponse response) {
            super(response);
            buffer = new ByteArrayOutputStream();
        }

        @Override
        public PrintWriter getWriter() throws IOException {
            if (outputType == OT_STREAM)
                throw new IllegalStateException();
            else if (outputType == OT_WRITER)
                return writer;
            else {
                outputType = OT_WRITER;
                writer = new PrintWriter(new OutputStreamWriter(buffer, getCharacterEncoding()));
                return writer;
            }
        }

        @Override
        public ServletOutputStream getOutputStream() throws IOException {
            if (outputType == OT_WRITER)
                throw new IllegalStateException();
            else if (outputType == OT_STREAM)
                return output;
            else {
                outputType = OT_STREAM;
                output = new WrappedOutputStream(buffer);
                return output;
            }
        }

        @Override
        public void flushBuffer() throws IOException {
            if (outputType == OT_WRITER)
                writer.flush();
            if (outputType == OT_STREAM)
                output.flush();
        }

        @Override
        public void reset() {
            outputType = OT_NONE;
            buffer.reset();
        }

        public byte[] body() throws IOException {
            flushBuffer();
            return null != buffer ? buffer.toByteArray() : new byte[0];
        }

        protected void finish() throws IOException {
            if (writer != null) {
                writer.close();
            }
            if (output != null) {
                output.close();
            }
        }

        class WrappedOutputStream extends ServletOutputStream {
            private ByteArrayOutputStream buffer;

            public WrappedOutputStream(ByteArrayOutputStream buffer) {
                this.buffer = buffer;
            }

            public void write(int b) throws IOException {
                buffer.write(b);
            }

            @Override public boolean isReady() {
                return false;
            }

            @Override public void setWriteListener(WriteListener writeListener) {

            }
        }
    }
} 