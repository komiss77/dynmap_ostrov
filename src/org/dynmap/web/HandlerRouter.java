package org.dynmap.web;

import org.dynmap.jetty.http.PathMap;
import org.dynmap.jetty.server.Handler;
import org.dynmap.jetty.server.Request;
import org.dynmap.jetty.server.handler.AbstractHandler;

import org.dynmap.javax.servlet.Servlet;
import org.dynmap.javax.servlet.ServletException;
import org.dynmap.javax.servlet.http.HttpServletRequest;
import org.dynmap.javax.servlet.http.HttpServletResponse;
import java.io.IOException;

@SuppressWarnings("deprecation")
public class HandlerRouter extends AbstractHandler {

    PathMap<HandlerOrServlet> pathMap = new PathMap<HandlerOrServlet>();
    
    private static class HandlerOrServlet {
    	Servlet servlet;
    	Handler handler;
    	HandlerOrServlet(Servlet s) { servlet = s; handler = null; }
    	HandlerOrServlet(Handler h) { servlet = null; handler = h; }
    };
    
    public HandlerRouter() {
    }
    
    public void addHandler(String path, Handler handler) {
        pathMap.put(path, new HandlerOrServlet(handler));
    }
    
    public void addServlet(String path, Servlet servlet) {
        pathMap.put(path, new HandlerOrServlet(servlet));
    }
    
    public void clear() {
        pathMap.clear();
    }
    
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
        String pathInfo = request.getPathInfo();
        PathMap.MappedEntry<HandlerOrServlet> e = pathMap.getMatch(pathInfo);
        String mappedPath = e.getMapped();

        String childPathInfo = pathInfo;
        if (mappedPath != null) {
            int i = 0;
            while(i<mappedPath.length() && mappedPath.charAt(i) == pathInfo.charAt(i)){ i++; }
            childPathInfo = childPathInfo.substring(i);
        }

        org.dynmap.jetty.server.Request r = (org.dynmap.jetty.server.Request)request;
        r.setPathInfo(childPathInfo);

        HandlerOrServlet o = e.getValue();
        if (o.handler != null) {
            o.handler.handle(target, baseRequest, request, response);
        }
        else if (o.servlet != null) {
            o.servlet.service(request, response);
        }

        r.setPathInfo(pathInfo);
    }
}
