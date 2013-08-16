/**
 * @author Yuanyuan Zhou
 * @date Oct 30, 2012
 * @organization University of Michigan, Ann Arbor
 */

package edu.umich.robustdatacollector.activeprobing;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.text.DecimalFormat;
import java.util.Random;

public class AcpbUtilities {
    public static String genRandomString(int len){
        StringBuilder sb = new StringBuilder("");
        Random ran = new Random();
        for(int i = 1; i <= len; i++){
            sb.append((char)('a' + ran.nextInt(26)));
        }
        return sb.toString();
    }
    
    public static boolean writeToSDCard(String str, String filename, String path) {
        File folder = new File(path);
        if (!folder.exists())
            folder.mkdirs();
        File f = new File(path, filename);
        if (!f.exists()) {
            try {
                f.createNewFile();
            } catch (IOException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
        }
        
        BufferedWriter out = null;  
        try {  
            out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(f, true)));  
            out.write(str);
            out.flush();
        } catch (Exception e) { 
            e.printStackTrace();
        } finally {  
            try {  
                out.close();  
            } catch (IOException e) {  
                e.printStackTrace();  
            }  
        }
        
        return true;
    }
    
    public static double[] pushResult(double[] results, double res){
        double[] tmp = results.clone();
        results = new double[tmp.length + 1];
        for(int i = 0; i < tmp.length; i++)
            results[i] = tmp[i];
        results[tmp.length] = res;
        return results.clone();
    }
    
    public static double roundDouble(double src) {
        DecimalFormat df = new DecimalFormat("#0.00");
        return Double.valueOf(df.format(src));
    }
}
