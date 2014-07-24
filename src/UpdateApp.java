package nl.portalsinc.updater;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ProgressBar;

import com.phonegap.hello_world.R;

public class UpdateApp extends CordovaPlugin {
	
	/* Controleer het pad van het versienummer */
	private String checkPath;
	/* Nieuw versienummer */
	private int newVerCode;
	/* Nieuwe versienaam */
	private String newVerName;
	/* APK Downloadpad */
	private String  downloadPath;
	/* Download */
    private static final int DOWNLOAD = 1;
    /* Einde download */
    private static final int DOWNLOAD_FINISH = 2;
    /* Download wegschrijven */
    private String mSavePath;
    /* Voortgangsbalk */
    private int progress;
    /* Update annuleren */
    private boolean cancelUpdate = false;
    /* Context */
    private Context mContext;
    /* Voortgangsbalk  */
    private ProgressBar mProgress;
    private Dialog mDownloadDialog;
	
    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        this.mContext = cordova.getActivity();
		if (action.equals("checkAndUpdate")) {
			this.checkPath = args.getString(0);

        	checkAndUpdate();
        	callbackContext.success();
        	return true;
		}
		return false;

    }
    /**
     * Controleren op udates
     */
    private void checkAndUpdate(){
    	if(getServerVerInfo()){
    		int currentVerCode = getCurrentVerCode();
    		if(newVerCode>currentVerCode){
    			this.showNoticeDialog();
    		}
    	}
    }
	
	
	/**
     * Klik voor de huidige versie van de apllicatie
     * @param context
     * @return
     */
    private int getCurrentVerCode(){
    	String packageName = this.mContext.getPackageName();
    	int currentVer = -1;
    	try {
			currentVer = this.mContext.getPackageManager().getPackageInfo(packageName, 0).versionCode;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
    	return currentVer;
    }
    
    /**
     * Naam van de huidige applicatie
     * @param context
     * @return
     */
    private String getCurrentVerName(){
    	String packageName = this.mContext.getPackageName();
    	String currentVerName = "";
    	try {
    		currentVerName = this.mContext.getPackageManager().getPackageInfo(packageName, 0).versionName;
		} catch (NameNotFoundException e) {
			e.printStackTrace();
		}
    	return currentVerName;
    }
    
    /**
     * Ophalen naam
     * @param context
     * @return
     */
    private String getAppName(){
    	return this.mContext.getResources().getText(R.string.app_name).toString();
    }
    
    /**
     * Versieinformatie van de server ophalen
     * @param path
     * @return
     * @throws Exception
     */
    private boolean getServerVerInfo(){
		try {
			StringBuilder verInfoStr = new StringBuilder();
			URL url = new URL(checkPath);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(5000);
			conn.setReadTimeout(5000);
			conn.connect();
			BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(),"UTF-8"),8192);
			String line = null;
			while((line = reader.readLine()) != null){
				verInfoStr.append(line+"\n");
			}
			reader.close();
			
			if(verInfoStr.length()>0){
				JSONObject obj = new JSONObject(verInfoStr.toString());				
				newVerCode = obj.getInt("verCode");
				newVerName = obj.getString("verName");
				downloadPath = obj.getString("apkPath");
			}
		} catch (Exception e) {
			e.printStackTrace();
			return false;
		} 
    	
    	return true;
    	
    }
    
    /**
     * Dialoogvenster software update
     */
    private void showNoticeDialog() {
    	
        // Dialoogostructuur
        AlertDialog.Builder builder = new Builder(mContext);
        builder.setTitle(R.string.soft_update_title);
        builder.setMessage(R.string.soft_update_info);
        // Update
        builder.setPositiveButton(R.string.soft_update_updatebtn, new OnClickListener(){
            public void onClick(DialogInterface dialog, int which){
                dialog.dismiss();
                // Dialoogvenster download tonen
                showDownloadDialog();
            }
        });
        // Nieuwere uppdates
        builder.setNegativeButton(R.string.soft_update_later, new OnClickListener(){
            public void onClick(DialogInterface dialog, int which){
                dialog.dismiss();
            }
        });
        Dialog noticeDialog = builder.create();
        noticeDialog.show();
    }

    /**
     * Software download dialoog tonen
     */
    private void showDownloadDialog()
    {
        // Software installeren dialoog
        AlertDialog.Builder builder = new Builder(mContext);
        builder.setTitle(R.string.soft_updating);
        // Voortgang installatie
        final LayoutInflater inflater = LayoutInflater.from(mContext);
        View v = inflater.inflate(R.layout.softupdate_progress, null);
        mProgress = (ProgressBar) v.findViewById(R.id.update_progress);
        builder.setView(v);
        // Update annuleren
        builder.setNegativeButton(R.string.soft_update_cancel, new OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
                // Annuleren toegestaan
                cancelUpdate = true;
            }
        });
        mDownloadDialog = builder.create();
        mDownloadDialog.show();
        // .apk downloaden
        downloadApk();
    }

    /**
     * Download .apk bestand
     */
    private void downloadApk()
    {
        // Start nieuwe download
        new downloadApkThread().start();
    }

    private Handler mHandler = new Handler()
    {
        public void handleMessage(Message msg)
        {
            switch (msg.what)
            {
            // Download
            case DOWNLOAD:
                // Voortgangsbalk
                mProgress.setProgress(progress);
                break;
            case DOWNLOAD_FINISH:
                // Te installeren bestand
                installApk();
                break;
            default:
                break;
            }
        };
    };
    
    /**
     * Download thread
     */
	private class downloadApkThread extends Thread {
		@Override
		public void run() {
			try {
				// Bepalen of er een SD/kaart is, met de lees- en schrijfrechten
				if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
					// Pad naar de geheugenkaart
					String sdpath = Environment.getExternalStorageDirectory()+ "/";
					mSavePath = sdpath + "download";
					URL url = new URL(downloadPath);
					// Verbinding maken
					HttpURLConnection conn = (HttpURLConnection) url.openConnection();
					conn.connect();
					// Get file size
					int length = conn.getContentLength();
					// Inputstream
					InputStream is = conn.getInputStream();

					File file = new File(mSavePath);
					// Check file directory
					if (!file.exists()) {
						file.mkdir();
					}
					File apkFile = new File(mSavePath, newVerName);
					FileOutputStream fos = new FileOutputStream(apkFile);
					int count = 0;
					// Cache
					byte buf[] = new byte[1024];
					// Naar bestand schrijven
					do {
						int numread = is.read(buf);
						count += numread;
						// Voortgangsbalk
						progress = (int) (((float) count / length) * 100);
						// Voortgang bijwerken
						mHandler.sendEmptyMessage(DOWNLOAD);
						if (numread <= 0) {
							// Download voltooid
							mHandler.sendEmptyMessage(DOWNLOAD_FINISH);
							break;
						}
						// Schrijf naar bestand
						fos.write(buf, 0, numread);
					} while (!cancelUpdate);// Klik op annuleren om te stoppen met downloaden.
					fos.close();
					is.close();
				}
			} catch (MalformedURLException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			// Downloadvenster wegdrukken
			mDownloadDialog.dismiss();
		}
	};

	/**
	 * Installler .apk bestand
	 */
	private void installApk() {
		File apkfile = new File(mSavePath, newVerName);
		if (!apkfile.exists()) {
			return;
		}
		// Installer .apk bestanden via webintent
		Intent i = new Intent(Intent.ACTION_VIEW);
		i.setDataAndType(Uri.parse("file://" + apkfile.toString()),
				"application/vnd.android.package-archive");
		mContext.startActivity(i);
	}
    

}
