package base;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import javax.swing.JOptionPane;

import base.DbConn;



public class Load_File
{
  public static final boolean lTest = true;
  public static final boolean lEmail = true;
  
  public Load_File() {}
  
  public static void main(String[] args)
    throws IOException, SQLException
  {
    Logger logger = null;
    try
    {
      boolean append = true;
      FileHandler fh = new FileHandler("CMFT_Mail.log", append);
      
      fh.setFormatter(new SimpleFormatter());
      logger = Logger.getLogger("log_name");
      logger.addHandler(fh);
    }
    catch (IOException e) {
      e.printStackTrace();
    }
    
    logger.info("Running Legal Libs");
    
    System.out.println("Begin Load_File");

    ResultSet resultset0 = null;
    
    ResultSet resultset1 = null;
    


    ResultSet resultset2 = null;
    

    DbConn db = new DbConn();
    try
    {
      db.getConnection();
    }
    catch (ClassNotFoundException|SQLException e1) {
      e1.printStackTrace();
    }

    boolean lRunProcess = false;
    

    String cMatterKey = "";
    String cMatterNumber = "";
    String cMatterName = "";
    String cBaseKey = "";
    
    String cEmailAddr = "";
    

    String cSQL00 = "select a.base_key,a.updated_by,a.date_updated ";
    cSQL00 = cSQL00 + "from cmft_base a ";
    cSQL00 = cSQL00 + "where a.process = 'Y' ";
    

    cSQL00 = cSQL00 + "order by a.date_updated";
    


    try
    {
      resultset0 = DbConn.execSQL(cSQL00);

    }
    catch (SQLException e)
    {
      e.printStackTrace();
    }
    

    while (resultset0.next()) {
      cBaseKey = resultset0.getString("base_key");
      
      System.out.println("Matter to process base key: " + cBaseKey);
      


      String cSQL01 = "select a.base_key,c.dynamic,c.matter_type_key,a.updated_by,b.template_key,c.template_folder,c.template_name,d.matter_number,d.matter_name,d.matter_key,f.eaddress ";
      cSQL01 = cSQL01 + "from cmft_base a, cmft_selected_templates b, cmft_templates c, matter d, personnel e, eaddress f ";
      cSQL01 = cSQL01 + "where a.process = 'Y' ";
      cSQL01 = cSQL01 + "and a.base_key = '" + cBaseKey + "' ";
      cSQL01 = cSQL01 + "and a.matter_key = d.matter_key ";
      cSQL01 = cSQL01 + "and a.base_key = b.base_key ";
      cSQL01 = cSQL01 + "and b.template_key = c.template_key ";
      cSQL01 = cSQL01 + "and a.updated_by = e.personnel_key ";
      cSQL01 = cSQL01 + "and e.object_key = f.entity_key ";
      cSQL01 = cSQL01 + "order by a.base_key";
      try
      {
        resultset1 = DbConn.execSQL(cSQL01);

      }
      catch (SQLException e)
      {
        e.printStackTrace();
      }
      


      String cSQL02 = "select a.base_key,a.updated_by,b.template_key,c.matter_type_key,c.template_folder,c.template_name,d.matter_number,d.matter_name,d.matter_key,f.eaddress ";
      cSQL02 = cSQL02 + "from cmft_base a, cmft_selected_templates b, cmft_templates c, matter d, personnel e, eaddress f ";
      cSQL02 = cSQL02 + "where a.process = 'Y' ";
      cSQL02 = cSQL02 + "and a.base_key = '" + cBaseKey + "' ";
      cSQL02 = cSQL02 + "and a.matter_key = d.matter_key ";
      cSQL02 = cSQL02 + "and a.base_key = b.base_key ";
      cSQL02 = cSQL02 + "and b.template_key = c.template_key ";
      cSQL02 = cSQL02 + "and c.dynamic in ('Y','M') ";
      cSQL02 = cSQL02 + "and a.updated_by = e.personnel_key ";
      cSQL02 = cSQL02 + "and e.object_key = f.entity_key ";
      cSQL02 = cSQL02 + "order by a.base_key";
      try
      {
        resultset2 = DbConn.execSQL(cSQL02);

      }
      catch (SQLException e)
      {
        e.printStackTrace();
      }
      
      String cMatterType;
      
      while (resultset2.next())
      {
        System.out.println("Dynamic Template Found");
        
        cMatterNumber = resultset2.getString("matter_number");
        int nMatterKey = resultset2.getInt("matter_key");
        cMatterKey = Integer.toString(nMatterKey);
        
        System.out.println(cMatterNumber);
        


        String[] aMergeArray = new String[2];
        
        aMergeArray[0] = cMatterNumber;
        aMergeArray[1] = cMatterKey;
        
        String cTemplateKey = "";
        cTemplateKey = resultset2.getString("template_key");
        
        int nTemplateKey = Integer.parseInt(cTemplateKey);
        
        cMatterType = "";
        cMatterType = resultset2.getString("matter_type_key");
        
        System.out.println(cMatterType);
        

        switch (nTemplateKey)
        {
        case 73: 
          try {
            System.out.println("Merging Dynamic Template");
            
            Merge_73.main(aMergeArray);
          }
          catch (Exception e) {
            e.printStackTrace();
          }
        
        case 77: 
          try
          {
            System.out.println("Merging EEO Office of Resolution Dynamic Template");
            
            Merge_77.main(aMergeArray);
          }
          catch (Exception e) {
            e.printStackTrace();
          }
        
        case 78: 
          try
          {
            System.out.println("EEOC Template Ltr Applnt Rep Req Auth final");
            
            Merge_78.main(aMergeArray);
          }
          catch (Exception e) {
            e.printStackTrace();
          }
        
        case 80: 
          try
          {
            System.out.println("EEOC Template Ltr Applnt Rep Req Auth final");
            
            Merge_80.main(aMergeArray);
          }
          catch (Exception e) {
            e.printStackTrace();
          }
        
        case 84: 
          try
          {
            System.out.println("MSPB Ltr Applnt re refuse release");
            
            Merge_84.main(aMergeArray);
          }
          catch (Exception e) {
            e.printStackTrace();
          }
        }
        
      }
      

      List<String> strList0 = new ArrayList();
     
      List<String> strListNameOnly = new ArrayList();
      
      boolean lFirstRow = true;
      

      while (resultset1.next()) {
        lRunProcess = true;
        
        cEmailAddr = resultset1.getString("eaddress");
        cMatterNumber = resultset1.getString("matter_number");
        cMatterName = resultset1.getString("matter_name");
        

        if (resultset1.getString("dynamic").equals("Y")) {
          System.out.println("Dynamic");
          
          strListNameOnly.add(cMatterNumber + "_" + resultset1.getString("template_name"));

        }
        else
        {
          System.out.println("Normal");
          strListNameOnly.add(resultset1.getString("template_name"));
        }
        
        strList0.clear();
        
        strList0.add(resultset1.getString("template_folder"));
        
        if (resultset1.getString("dynamic").equals("Y")) {
          strList0.add(resultset1.getString("matter_number") + "_" + resultset1.getString("template_name"));
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
          
          File theDir = new File("processed\\" + cMatterNumber);
          try {
            if (!theDir.exists()) {
              cMatterType = theDir.mkdir();
            }
            else
            {
              File[] files = theDir.listFiles();
              for (int i = 0; i < files.length; i++) {
                System.out.println("|||||||||||||||||File Deleted: " + files[i] + "|||||||||||||||||");
                files[i].delete();
              }
            }
          }
          catch (Exception e) {
            JOptionPane.showMessageDialog(null, e);
          }
        }
        
        ProcessBase.process(strList0);
      }
      
      if (lRunProcess)
      {

        String[] aZipArray = new String[3];
        
        cMatterNumber = (String)strList0.get(3);
        aZipArray[0] = ("processed\\" + cMatterNumber);
        aZipArray[1] = ("processed\\" + cMatterNumber + ".zip");

        aZipArray[2] = ("LL" + cMatterNumber.substring(0, 6));
        
        ZipPassFolder.main(aZipArray, strListNameOnly);
        

        String[] aMailArray = new String[4];
        aMailArray[0] = cEmailAddr;
        aMailArray[1] = cMatterNumber;
        aMailArray[2] = cMatterName;
        aMailArray[3] = aZipArray[1];
        
        String cSuccess = "";
        
        cSuccess = Send_Mail.SendMail(aMailArray, strListNameOnly);
        logger.info("Status of Email:  " + cSuccess);
        

        String cProcess = "N";
        
        if (cSuccess.equals("Failed")) {
          cProcess = "F";
        }
        
        String cSQL03 = "update cmft_base set  process = '" + cProcess + "', date_processed=sysdate, processed_by = 100000 where base_key = " + cBaseKey;
        
        try
        {
          resultset2 = DbConn.execSQL(cSQL03);

        }
        catch (SQLException e)
        {
          e.printStackTrace();
        }
        
        strListNameOnly.clear();
      }
    }
  }
}