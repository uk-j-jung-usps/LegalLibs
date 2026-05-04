<%@page contentType="text/html; pageEncoding=UTF-8" language="java"
	import="java.util.*,java.text.*,java.sql.*,javax.xml.parsers.*,org.w3c.dom.*,test.DbBean.*"
	errorPage=""%>
<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN"
    "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=UTF-8">
<link rel="stylesheet" type="text/css" href="upload.css">
<title>Litigation Hold File Uploads</title>
</head>

<jsp:useBean id="db" scope="request" class="test.DbBean" />
<jsp:setProperty name="db" property="*" />

<%!
	ResultSet rs = null;
	ResultSetMetaData rsmd = null;

	ResultSet rs1 = null;
	ResultSetMetaData rsmd1 = null;
	
	int numColumns;
	int nRows;
	int i;
	boolean lTesting=false;
%>

<style>
body {
	background-color: #bfdfff;
	color: black;
    font-style: normal;
    font-family: Sans-serif;
}
h2 {
    color: #000000;
    font-style: normal;
    font-family: Sans-serif;
}
h3 {
	background-color: #000000;
    color: red;
    font-style: normal;
    font-family: Sans-serif;
    text-align: center;
}
</style>

<jsp:include page="varsandmore.js" />
<jsp:include page="display.js" />
<jsp:include page="doctype.js" />
<jsp:include page="ajaxdelete.js" />
<jsp:include page="ajaxbefore.js" />
<jsp:include page="ajaxduring.js" />
<jsp:include page="upload.js" />
<jsp:include page="aes.js" />

<%
	String cAceID = "";
	String cMatterNo = "";
	String cMatterKey = "";
	
	if (request.getParameter("ThisMatterNumber") != null) {
		cMatterNo = request.getParameter("ThisMatterNumber");
	} else {
		cMatterNo = "xxxxxx";
	}

	if (request.getParameter("CLIENT_USERID") != null) {
		cAceID = request.getParameter("CLIENT_USERID");
	} else {
		cAceID = "xxxxxx";
	}

	if (request.getParameter("LIT_HOLD_MATTER_KEY") != null) {
		cMatterKey = request.getParameter("LIT_HOLD_MATTER_KEY");
	} else {
		cMatterKey = "123456";
	}

	String cL_H_N_R_PKEY = "";
	if (request.getParameter("L_H_N_R_PKEY") != null) {
		cL_H_N_R_PKEY = request.getParameter("L_H_N_R_PKEY");
	} else {
		cL_H_N_R_PKEY = "123456";
	}
	
	db.connect();	
	
	String sql0;
	
	sql0 = "select t.document_type_key,t.description from lit_hold_doctype t where t.disabled = 'N' order by t.sortorder";
	
	try {
		rs = db.execSQL(sql0);
	}
	catch(SQLException e) { 
		throw new ServletException("Your sql0 query is not working", e);    
	}

	String sql1;
	
	sql1 = "select t.lh_doc_key,t.document_type_key,t.description,t.file_name,t.alt_file_name from lit_hold_files t ";
	sql1 = sql1+"where t.matter_key = '"+cMatterKey+"' and t.client_userid = '"+cAceID+"' and t.status = 'S' order by t.lh_doc_key";
	
	if (lTesting) {
		out.println("*************************************************<br>");
		out.println(sql1);
		out.println("<br>*************************************************<br>");
	}
		
	try {
		rs1 = db.execSQL(sql1);
	}
	catch(SQLException e) { 
		throw new ServletException("Your sql0 query is not working", e);    
	}
	
	
	String cDesc="";
	String cFile="";
	int cDocKey=0;
	int cDocTypeKey=0;
	
	int cKey=0;
	
	if (lTesting) {
		out.println("*************************************************<br>");
	}
	//if (rs.next()) {
	while(rs.next()){
		cDesc = rs.getString("description");
		cKey = rs.getInt("document_type_key");
		%>
		<script>populateArray('<%out.print(cDesc);%>','<%out.print(cKey);%>');</script>
		<%
		if (lTesting) {
			//out.println("*************************************************<br>");
			//out.println(sql0);
			out.println("::");
			out.println(cDesc);
			//out.println("<br>*************************************************<br>");
		}
	}	

	if (lTesting) {
		out.println("<br>*************************************************<br>");
	}
	
%>

<body>
	<div style="color:#000000" align="center">
		<h2>Litigation Hold - Choose Files to Upload</h2>
		<form action="upload" id="frmUpload" name="frmUpload" method="post" enctype="multipart/form-data">

			<h5>[Law Department Reference: Matter No. <%out.print(cMatterNo);%>]</h5>
	

			<input type="hidden" value="<%out.print(cAceID);%>" name="CLIENT_USERID" id="CLIENT_USERID" />
			<input type="hidden" value="<%out.print(cMatterKey);%>" name="LIT_HOLD_MATTER_KEY" id="LIT_HOLD_MATTER_KEY"/>
			<input type="hidden" value="<%out.print(cMatterNo);%>" name="ThisMatterNumber" id="ThisMatterNumber"/>
			<input type="hidden" value="Y" name="EnCrypt"  id="EnCrypt"/>
			<input type="hidden" value="<%out.print(cL_H_N_R_PKEY);%>" name="cLHKey" />
			<input type="hidden" id="cTime" name="cTime" /><br>

			<table id="cTbl" width="1270" cellpadding="2" cellspacing="2" bgcolor="#ccffcc" border="0">
				
				<tr><td colspan=4 align=left>
					<input type="button" value="Add File to Upload"
						id="btnAdd" name="btnAdd" onClick="addRow()"/>
					</td>
					
				</tr>
<%
 	/* 
 	           <input type="button" value="Run AJAX" id="btnAJAX" onClick="runAjax()" />
 	           <input type="button" value="Get File Total" id="btnFT" onClick="getFileTotal()" />
 	 */
 %>
				<tr>
					<td colspan=4 width="80%">&nbsp;<b id="label">&nbsp;</b></td>
				</tr>


				<tr>
					<td width="2%">&nbsp;</td>
					<td width="25%">&nbsp;<b>Select File</b></td>
					<td width="30%">&nbsp;<b>Select Document Type</b></td>
					<td width="43%">&nbsp;<b>Enter Description</b></td>
				</tr>

			</table>

			<table id="cTbl2" width="1270" cellpadding="2" cellspacing="2"
				bgcolor="#FFFFCC" border="0">
				<tr>
					<td colspan=5 align=left><input type="button"
						value="Complete Upload Process" id="btnUpdate" onClick="upload();" />
						<%
						//<input type="button" value="Test Insert" id="btnTest" onClick="runAjaxBeforeUpload()" />
						%>
						<input type="button" value="Display History" id="btnHist" onClick="displayHistory()" />
						 
						 
					</td>
				</tr>
			</table>

			<script>
				setTimeStamp();
			</script>


			<%
			boolean lLoop = false;
			boolean lWarning = true;
			while(rs1.next()){

				if (lWarning){
					lWarning = false;
					%>
					<script>
					addWarning();
					</script>
					<%
				}
				
				//If in while loop we set lLoop to true so no additional blank row is added to the display
				lLoop = true;
				cDocKey = rs1.getInt("lh_doc_key");

				cFile= rs1.getString("file_name");
				if (cFile != null){
					
					cFile=cFile.substring(cFile.lastIndexOf('\\')+1);
					
					/*
					if (cFile.indexOf('\\')!=-1){
						File f = new File(cFile);
						cFile = f.getName();
					}
					*/
				} else {
					cFile="";
				}
				cDocTypeKey = rs1.getInt("document_type_key");

				cDesc = rs1.getString("description");
				
				if (cDesc == null){
					cDesc="";
				}
				
		      	/*
				out.println(cDocKey);
				out.println(cFile);
				out.println(cDocTypeKey);
				out.println(cDesc+"<br>");
				*/
				//nNew,cDocKey,cFile,cDocTypeKey,cDesc
				%>
				
				<script>
					addRow(0,"<%out.print(cDocKey);%>","<%out.print(cFile);%>","<%out.print(cDocTypeKey);%>","<%out.print(cDesc);%>");
				</script>
				<%
			}	

			if (!lLoop){
			%>				

			<script>
				addRow(1,"","");
			</script>
			<%}%>

		</form>
	</div>

</body>
</html>