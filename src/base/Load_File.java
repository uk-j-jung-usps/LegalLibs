package base;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

//import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
//import java.sql.Statement;

import javax.swing.JOptionPane;

//import oracle.jdbc.pool.OracleDataSource;

public class Load_File {

	//Acts like a global variable and is referenced in other packages as LoadFile.TEST_MODE
	public final static boolean TEST_MODE = true;
	//Set to false when testing and you do not want email sent
	public final static boolean EMAIL_ENABLED = true;
	
	//public void main(String[] args, Object String) throws IOException {
	public static void main(String[] args) throws IOException, SQLException {

		//Added to create a log for email being sent
		Logger logger = null;

		try {
			boolean append = true;
			FileHandler fh = new FileHandler("CMFT_Mail.log", append);
			//fh.setFormatter(new XMLFormatter());
			fh.setFormatter(new SimpleFormatter());
			logger = Logger.getLogger("log_name");
			logger.addHandler(fh);
		}
		catch (IOException e) {
			e.printStackTrace();
		}

		logger.info("Running Legal Libs");

		if (LoadFile.TEST_MODE) System.out.println("Begin LoadFile"); 


		//***************************************************************************************************
		//Database connection statements
		//Connection connection;

		//List of matters to process
		//Statement statement0 = null;
		ResultSet resultset0 = null;

		//List of matter keys and templates to process
		//Statement statement1 = null;
		ResultSet resultset1 = null;

		//Used to update table CMFT_BASE setting process to N and update date processed and processed by
		//Statement statement2 = null;
		ResultSet resultset2 = null;

		DbConn db;
		db=new DbConn();
		
		try {
			db.getConnection();
		} catch (SQLException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		

		/*
		// No longer being used. Now using DbBean
		// Using JDBC
		OracleDataSource ods;
		ods = new OracleDataSource();

		//Production
		//String url = "jdbc:oracle:thin:@//eagnmntwe189c:1521/plawdb.usps.gov";
		//Development
		String url = "jdbc:oracle:thin:@eagnmnsxn41:1521/dlawdb";
		//CAT
		//String url = "jdbc:oracle:thin:@eagnmnss06e:1521/qlawdb";
		//Test
		//String url = "jdbc:oracle:thin:@56.64.80.197:1521/tlawdb";

		ods.setURL(url);
		ods.setUser("lawmanager");
		ods.setPassword("xxxxxxxx");
		connection = ods.getConnection();

		//End of Database connection statements
		//***************************************************************************************************
		*/

		//Set cRunProcess to Y if templates are found to run
		boolean lRunProcess = false;

		//String cMatterKey = "606517";
		String cMatterKey = "";
		String cMatterNumber = "";
		String cMatterName = "";
		String cBaseKey = "";
		//String cCompBaseKey = "";
		String cEmailAddr = "";

		//Query CMFT_BASE to see if there are any matter keys to be processed
		String cSQL00 = "select a.base_key,a.updated_by,a.date_updated ";
		cSQL00 = cSQL00 +"from lawmanager.cmft_base a "; 
		cSQL00 = cSQL00 +"where a.process = 'Y' ";
		//Used if being passed a matter key from CGI or anything else
		//cSQL01 = cSQL01 +"and a.matter_key = '"+cMatterKey"' ";
		cSQL00 = cSQL00 +"order by a.date_updated";

		try {
			//statement0 = connection.createStatement();
			//resultset0 = statement0.executeQuery(cSQL00);
			
			resultset0 = DbConn.execSQL(cSQL00);
			

		} catch (SQLException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		//Go through list of matter keys to process
		while (resultset0.next()) {
			cBaseKey = resultset0.getString("base_key");

			if (LoadFile.TEST_MODE) System.out.println("Matter to process base key: "+cBaseKey); 


			//Query all templates by base key to be process
			String cSQL01 = "select a.base_key,c.dynamic,c.matter_type_key,a.updated_by,b.template_key,c.template_folder,c.template_name,d.matter_number,d.matter_name,d.matter_key,f.eaddress ";
			cSQL01 = cSQL01 +"from lawmanager.cmft_base a, lawmanager.cmft_selected_templates b, lawmanager.cmft_templates c, lawmanager.matter d, lawmanager.personnel e, lawmanager.lawmanager.eaddress f "; 
			cSQL01 = cSQL01 +"where a.process = 'Y' ";
			cSQL01 = cSQL01 +"and a.base_key = '"+cBaseKey+"' ";
			cSQL01 = cSQL01 +"and a.matter_key = d.matter_key ";
			cSQL01 = cSQL01 +"and a.base_key = b.base_key ";
			cSQL01 = cSQL01 +"and b.template_key = c.template_key ";
			cSQL01 = cSQL01 +"and a.updated_by = e.personnel_key ";
			cSQL01 = cSQL01 +"and e.object_key = f.entity_key "; 
			cSQL01 = cSQL01 +"order by a.base_key";

			try {
				resultset1 = DbConn.execSQL(cSQL01);


			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			
			//Query dynamic templates by base key to be process, checks for dynamic = 'Y' and includes matter type key of template
			String cSQL02 = "select a.base_key,a.updated_by,b.template_key,c.matter_type_key,c.template_folder,c.template_name,d.matter_number,d.matter_name,d.matter_key,f.eaddress ";
			cSQL02 = cSQL02 +"from lawmanager.cmft_base a, lawmanager.cmft_selected_templates b, lawmanager.cmft_templates c, lawmanager.matter d, lawmanager.personnel e, lawmanager.eaddress f "; 
			cSQL02 = cSQL02 +"where a.process = 'Y' ";
			cSQL02 = cSQL02 +"and a.base_key = '"+cBaseKey+"' ";
			cSQL02 = cSQL02 +"and a.matter_key = d.matter_key ";
			cSQL02 = cSQL02 +"and a.base_key = b.base_key ";
			cSQL02 = cSQL02 +"and b.template_key = c.template_key ";
			cSQL02 = cSQL02 +"and c.dynamic in ('Y','M') ";
			cSQL02 = cSQL02 +"and a.updated_by = e.personnel_key ";
			cSQL02 = cSQL02 +"and e.object_key = f.entity_key "; 
			cSQL02 = cSQL02 +"order by a.base_key";

			try {
				resultset2 = DbConn.execSQL(cSQL02);


			} catch (SQLException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			

			//Go through list of dynamic templates to process by matter key
			while (resultset2.next()) {
				
				if (LoadFile.TEST_MODE) System.out.println("Dynamic Template Found");
				
				cMatterNumber = resultset2.getString("matter_number");
				int nMatterKey = resultset2.getInt("matter_key");
				cMatterKey = Integer.toString(nMatterKey);
				
				if (LoadFile.TEST_MODE) System.out.println(cMatterNumber);

				String[] aMergeArray;
				
				aMergeArray = new String[2];

				aMergeArray[0]=cMatterNumber;
				aMergeArray[1]=cMatterKey;
				
				String cTemplateKey="";
				cTemplateKey = resultset2.getString("template_key");
				
				int nTemplateKey = Integer.parseInt(cTemplateKey);

				String cMatterType="";
				cMatterType = resultset2.getString("matter_type_key");
				
				if (LoadFile.TEST_MODE) System.out.println(cMatterType);
				
				//int nMatterType = Integer.parseInt(cMatterType);
				
				//GAC - 06/17/2015
				//Changing switch from nMatterType to nTemplateKey since the template key specifies a specific template and is unique.
				//Using nMatterType will not be unique as we add more dynamic templates.
				
				switch (nTemplateKey) {
				
				case 73: ;	//SF Advice template 73
					try {
						if (LoadFile.TEST_MODE) System.out.println("Merging Dynamic Template");

						Merge_73.main(aMergeArray);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					break;

				case 77: ;	//SF EEOC template 77
				try {
					if (LoadFile.TEST_MODE) System.out.println("Merging EEO Office of Resolution Dynamic Template");

					Merge_77.main(aMergeArray);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
					
				case 78: ;	//SF EEOC template 78
				try {
					if (LoadFile.TEST_MODE) System.out.println("EEOC Template Ltr Applnt Rep Req Auth final");

					Merge_78.main(aMergeArray);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;

				case 80: ;	//SF MSPB template 80
				try {
					if (LoadFile.TEST_MODE) System.out.println("EEOC Template Ltr Applnt Rep Req Auth final");

					Merge_80.main(aMergeArray);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
				
				case 84: ;	//SF MSPB template 84
				try {
					if (LoadFile.TEST_MODE) System.out.println("MSPB Ltr Applnt re refuse release");

					Merge_84.main(aMergeArray);
				} catch (Exception e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				break;
				
				//Discovery - Agency's Standard Discovery Responses
				//EEOC version - template 4
				//MSPB version - template 52
				//Not being used at this time...
				/*
				default: 
					try {
						Merge_Files.main(aMergeArray);
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					break;
				*/
				
				}
				
			}

			//Used to hold list of templates temporarily
			List<String> strList0 = new ArrayList<String>();

			//Used to hold list of all templates processed
			List<String> strListNameOnly = new ArrayList<String>();
			

			//Used to hold variables with values (moved to ProcessBase)
			//List<String> strList = new ArrayList<String>();

			boolean lFirstRow = true;
			
			//Go through list of templates to process by matter key
			while (resultset1.next()) {
				lRunProcess = true;
				
				cEmailAddr = resultset1.getString("eaddress");
				cMatterNumber = resultset1.getString("matter_number");
				cMatterName = resultset1.getString("matter_name");

				//If dynamic add matter_number_ as prefix to template
				if (resultset1.getString("dynamic").equals("Y")){
					if (LoadFile.TEST_MODE) System.out.println("Dynamic");
					
					strListNameOnly.add(cMatterNumber+"_"+resultset1.getString("template_name"));
					
				}
				else
				{	
					if (LoadFile.TEST_MODE) System.out.println("Normal"); 
					strListNameOnly.add(resultset1.getString("template_name"));
				}
					
				strList0.clear();

				strList0.add(resultset1.getString("template_folder"));
				//If dynamic add matter_number_ as prefix to template, Y is dynamic, M is mixed, N is No
				if (resultset1.getString("dynamic").equals("Y")){
					strList0.add((resultset1.getString("matter_number")+"_"+resultset1.getString("template_name")));
				}
				else {
					strList0.add(resultset1.getString("template_name"));
				}
				strList0.add(resultset1.getString("template_key"));
				strList0.add(resultset1.getString("matter_number"));
				strList0.add(resultset1.getString("matter_name"));
				strList0.add(resultset1.getString("matter_key"));
				
				if (lFirstRow) {
					lFirstRow = false;
					
					File theDir = new File("processed\\"+cMatterNumber);  // Defining Directory/Folder Name  
					try{   
						if (!theDir.exists()){  // Checks that Directory/Folder Doesn't Exists!  
							boolean result = theDir.mkdir();    
							if(result){  
								//JOptionPane.showMessageDialog(null, "New Folder created!");
							}  
						} 
						else
						{
							//Delete all files in Directory
							File[] files = theDir.listFiles();
							for(int i=0; i<files.length; i++) {
								if (LoadFile.TEST_MODE) System.out.println("|||||||||||||||||File Deleted: "+files[i]+"|||||||||||||||||"); 
								files[i].delete();
							}			

						}
					}catch(Exception e){  
						JOptionPane.showMessageDialog(null, e);  
					}  		
					
				}
				

				ProcessBase.process(strList0);
			}
			
			if (lRunProcess) {

		        String[] aZipArray;
		        aZipArray = new String[3];
		        
		        cMatterNumber = strList0.get(3);
		        aZipArray[0] = "processed\\"+cMatterNumber;
		        aZipArray[1] = "processed\\"+cMatterNumber+".zip";
		        //Password before made easy by request from users SF201140861
		        //aZipArray[2] = "cmft!"+cMatterNumber.substring(0,2)+cMatterNumber.substring(6);
		        //was cmft!SF40861 now LLSF2011
		        aZipArray[2] = "LL"+cMatterNumber.substring(0,6);

		        
				/*	
				 * 
				 * GAC - Not needed since we are doing all zip routines within Java
				Run batch file, this may be used to zip the contents of directory where the output files have been placed
				String path="cmd /c start d:\\sample\\sample.bat";
				Runtime rn=Runtime.getRuntime();
				Process pr=rn.exec(path);`
	
				GAC - No longer needed since we are now using an open source library Zip4j which includes encryption and password protection
				which the standard Java library does not. 
		        //Zip_Folder.main(aZipArray);
	            */
		        
				ZipPassFolder.main(aZipArray,strListNameOnly);
				
				// declares an array of integers
		        String[] aMailArray;
	
		        // allocates memory for 4 Strings
		        aMailArray = new String[4];
		        aMailArray[0]=cEmailAddr;
		        aMailArray[1]=cMatterNumber;
		        aMailArray[2]=cMatterName;
		        aMailArray[3]=aZipArray[1];
	
		        String cSuccess="";
        		if (EMAIL_ENABLED){
        			cSuccess = SendMail.SendMail(aMailArray,strListNameOnly);
        			logger.info("Status of Email:  "+cSuccess);
				}
        		
        		String cProcess= "N";
        		
        		if (cSuccess.equals("Failed")){
        			cProcess = "F";
        		}
        		
				//Update table CMFT_BASE setting process to N if successful or F if the send fails
				String cSQL03 = "update lawmanager.cmft_base set  process = '"+cProcess+"', date_processed=sysdate, processed_by = 100000 where base_key = "+cBaseKey;
	
				try {
					//statement2 = connection.createStatement();
					//resultset2 = statement2.executeQuery(cSQL03);
					resultset2 = DbConn.execSQL(cSQL03);
	
					
				} catch (SQLException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
				
				strListNameOnly.clear();
			}
		}
	}
}

