/*************************************************************************
 *
 * Copyright Notice (2006)
 * (c) CSC Financial Services Limited 1996-2006.
 * All rights reserved. The software and associated documentation
 * supplied hereunder are the confidential and proprietary information
 * of CSC Financial Services Limited, Austin, Texas, USA and
 * are supplied subject to licence terms. In no event may the Licensee
 * reverse engineer, decompile, or otherwise attempt to discover the
 * underlying source code or confidential information herein.
 *
 *************************************************************************/
package com.csc.fs.accel.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.InetAddress;
import java.util.Iterator;
import java.util.List;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import com.csc.fs.accel.ui.webservices.ServiceInvoke;

/**
 * Servlet that performs and manages hooks into the initialization and
 * configuration for the presentation tier environment when deployed in a
 * Servlet container
 * <p>
 * <b>Modifications:</b><br>
 * <table border=0 cellspacing=5 cellpadding=5>
 * <thead>
 * <th align=left>Project</th><th align=left>Release</th><th align=left>Description</th>
 * </thead>
 * <tr><td>NBA234</td><td>Version 8</td><td> nbA ACORD Transformation Service Project</td></tr>
 * </table>
 * <p>
 * @author CSC FSG Developer
 * @version 8.0.0 
 */
public class WebService extends HttpServlet {

    public static final String NOT_SUPPORTED_OVER_GET = "<html><body><p><b>Web Service Requests are not supported through an HTTP GET operation</b></p><p>Please submit request through a POST operation instead.</body></html>";

    public static final String WEB_SERVICE_INDEX_START = "<html><body><H1>Web Services Index:</H1>";
    public static final String WEB_SERVICE_INDEX_Info = "<p>Usage:<br>The web services exposed through this site enable an external group to access the systems internal services.<br>Before invoking any service calls the invoker must invoke the SIGNON service, this provides a SessionID which will be needed for all subsequent calls.<br>";
    public static final String WEB_SERVICE_INDEX_Info1 = "To access customer information, the invoker must first do a CUST_LOCATE to retrieve a list of customer candidates (This may be invoked multiple times to filter through the complete result set.<br>";
    public static final String WEB_SERVICE_INDEX_Info2 = "After a customer has been identified the invoker must use the CUST_SELECT service, this retrieves the customer and sets that customer as the primary.<br>";
    public static final String WEB_SERVICE_INDEX_Info3 = "To Access the information for that customer use the RETRIEVE_XXX services, this provides access to the various levels of information for the primary customer.<br>";
    public static final String WEB_SERVICE_INDEX_Info4 = "After all interactions have been completed the invoker should use the SIGNOFF service - note that the session will be terminated after a period of inactivity, but it is good practice to SIGNOFF explicitly<br>";
    public static final String WEB_SERVICE_INDEX_Info5 = "<br>An sdditional service is provided that gives access to the reference data lists (allowable values/codes used for drop down lists and lookups) in the system.<br>";
    public static final String WEB_SERVICE_INDEX_Info6 = "<br><br>Services are invoked through HTTP POST requests and the content of the post should be compliant with SOAP 1.2 Specifications and adhere to the appropriate WSDL for the intended service<br>The URL for the service must be for example:<br>http://dlapsrvt/Bravo/services/SERVICE_NAME where SERVICE_NAME is the name of the intended service such as SIGNON or CUST_LOCATE<br>";
        
    public static final String WEB_SERVICE_INDEX_TABLE_START="<table><tr><td><h2>Service Name</h2></td><td><h2>Description</h2></td></tr>";
    public static final String WEB_SERVICE_INDEX_END = "</table></body></html>";


    public String constructWSDL(String serviceName, HttpServletRequest request){
        String contextPath = request.getContextPath();
        return ServiceInvoke.Factory.getWSDL(serviceName, request.getServerName(), String.valueOf(request.getServerPort()), contextPath);
    }
    
    private String getServiceName(HttpServletRequest request){
        if (request.getPathInfo() != null) {  //NBA234
            return request.getPathInfo().substring(1);
        }
        return null;  //NBA234
    }

    /*
     * @see javax.servlet.http.HttpServlet#doGet(HttpServletRequest, HttpServletResponse)
     */
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
            
        String currentServiceName = getServiceName(request);
        if(ServiceInvoke.SERVICE_INDEX_NAME.equalsIgnoreCase(currentServiceName)){  //NBA234
            // build HTML Page with table of all Web Services, Descriptions and the links to thier respective WSDL's
            StringBuffer webServiceIndexPage = new StringBuffer(5000);
            webServiceIndexPage.append(WEB_SERVICE_INDEX_START).append(WEB_SERVICE_INDEX_Info).append(WEB_SERVICE_INDEX_Info1).append(WEB_SERVICE_INDEX_Info2).append(WEB_SERVICE_INDEX_Info3).append(WEB_SERVICE_INDEX_Info4).append(WEB_SERVICE_INDEX_Info5).append(WEB_SERVICE_INDEX_Info6).append(WEB_SERVICE_INDEX_TABLE_START);
            List services = ServiceInvoke.Factory.getServices();
            Iterator iter = services.iterator();
            while(iter.hasNext()){
                String serviceName = (String)iter.next();
                webServiceIndexPage.append("<tr><td><a href=\"").append(request.getContextPath()).append("/services/").append(serviceName).append("?WSDL\">").append(serviceName).append("</a></td><td>").append(ServiceInvoke.Factory.getServiceDescription(serviceName)).append("</td></tr>");
            }
            webServiceIndexPage.append(WEB_SERVICE_INDEX_END);
            response.getOutputStream().println(webServiceIndexPage.toString());
        } else if (request.getQueryString() != null && request.getQueryString().indexOf("WSDL") > -1){  //NBA234
            response.getOutputStream().println(constructWSDL(currentServiceName, request));
        } else {
            // note Web Service invocation not supported over a GET operation
            response.getOutputStream().println(NOT_SUPPORTED_OVER_GET);
        }
    }

    /*
     * @see javax.servlet.http.HttpServlet#doPost(HttpServletRequest, HttpServletResponse)
     */
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
        String currentServiceName = getServiceName(request);
        
        // default response to service not supported SOAP FAULT Message
        String resultXML = ServiceInvoke.SERVICE_NOT_SUPPORTED_SOAP_FAULT;
        String currentQueryString = request.getQueryString();
        if(currentQueryString != null && currentQueryString.indexOf("WSDL") > -1){
            // Set Result XML to be the WSDL for requested Service
            resultXML = constructWSDL(currentServiceName, request);
        } else {
            if(ServiceInvoke.Factory.isServiceSupported(currentServiceName)){
                // Invoke the Web Service Requested
                ServiceInvoke handler = ServiceInvoke.Factory.getInstance();
                if(handler != null){

                    String workstationID = request.getRemoteHost();
                    
                    if(workstationID.equals("127.0.0.2")){
                        try{
                            workstationID = InetAddress.getLocalHost().getHostName();
                        } catch (Exception ex){
                            workstationID = "LOCALHOST";
                        }
                    }
                    if(workstationID.length() > 8)
                        workstationID = workstationID.substring(0,8);
                    
                    
                    BufferedReader reader = request.getReader();
                    String current = reader.readLine();
                    StringBuffer currentInXML = new StringBuffer(5000);
                    while(current != null){
                        currentInXML.append(current);
                        current = reader.readLine();
                    }
                    String inputXML = currentInXML.toString();
                    System.out.println("Print input xml for userid and password"+ inputXML);
                    resultXML = (String)handler.execute(currentServiceName, inputXML, workstationID);
                }
            }
        }
        if(resultXML.indexOf("SOAP-ENV:Fault") > -1){
            // its an error so send back as a 500
            response.setStatus(500);
        }
        response.getOutputStream().println(resultXML);
    }


    public static class IndexSaxHandler extends DefaultHandler {

        public static String COMPONENT_TAG = "component";

        private boolean processingElement = false;

        private List currentComponentList = null;

        private String tempvalue;

        public void error(SAXParseException e) throws SAXException {
            super.error(e);
        }

        public void fatalError(SAXParseException e) throws SAXException {
            super.fatalError(e);
        }

        public void warning(SAXParseException e) throws SAXException {
            super.warning(e);
        }

        /**
         * Default Constructor
         */
        public IndexSaxHandler(List compList) {
            super();
            this.currentComponentList = compList;
        }

        /*
         * @see org.xml.sax.DefaultHandler#startElement(String,String,String,Attributes)
         */
        public void startElement(String uri, String name, String qName, Attributes atts) {
            processingElement = true;
            // reset the characters buffer for each new element
            tempvalue = "";
        }

        /*
         * @see org.xml.sax.DefaultHandler#endElement(String,String,String)
         */
        public void endElement(String uri, String name, String qName) {
            processingElement = false;
            if (name.equals(COMPONENT_TAG)) {
                if (currentComponentList != null) {
                    currentComponentList.add(tempvalue);
                }
            }
            tempvalue = "";
        }

        /*
         * @see org.xml.sax.DefaultHandler#characters(char[],int,int)
         */
        public void characters(char ch[], int start, int length) {
            String processItem = new String(ch, start, length);
            if (processingElement)
                tempvalue += processItem;
            else
                tempvalue = processItem;
            processingElement = true;
        }

        /**
         * @return Returns the currentComponentList.
         */
        public List getCurrentComponentList() {
            return currentComponentList;
        }

        /**
         * @param currentComponentList
         *            The currentComponentList to set.
         */
        public void setCurrentComponentList(List currentComponentList) {
            this.currentComponentList = currentComponentList;
        }
    }
    
    private static int insertRecordsForCensusTable(PreparedStatement prepStmt, Row nextRowEntry, java.sql.Date date, String employername,
			boolean faceAmountIndicator) throws Exception {
		int ind = 1;
		try {
			prepStmt.setString(1, GUIDFactory.getIdHexString());
			prepStmt.setDate(2, date);
			prepStmt.setString(3, nextRowEntry.getCell(5).toString());
			prepStmt.setString(4, nextRowEntry.getCell(6).toString());
			prepStmt.setString(5, nextRowEntry.getCell(7).toString().replace("-", ""));
			prepStmt.setString(6, employername.toUpperCase().trim());
			prepStmt.setString(7, "0");
			prepStmt.setDate(8, new java.sql.Date(new Date().getTime()));// 04-Mar-1978"/* 1969-04-14
			if (faceAmountIndicator) {
				if (((!NbaUtils.isBlankOrNull(nextRowEntry.getCell(11).toString()) || (!NbaUtils.isBlankOrNull(nextRowEntry.getCell(12).toString()))))) {
					prepStmt.setString(9, nextRowEntry.getCell(11).toString());
					prepStmt.setString(10, nextRowEntry.getCell(12).toString());
				} else {
					throw new Exception("Please enter either the New Face Amount or Current Face Amount");
				}

			} else {
				prepStmt.setString(9, null);
				prepStmt.setString(10, null);
			}
			prepStmt.executeUpdate();
		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
			ind = nextRowEntry.getRowNum();
			throw new Exception("nextRowEntry For Census Table: ");
		}

		return ind;
	}


}
