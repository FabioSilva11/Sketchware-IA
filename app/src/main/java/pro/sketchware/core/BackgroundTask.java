package pro.sketchware.core;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import pro.sketchware.R;
import pro.sketchware.utility.TranslationFunction;

import pro.sketchware.beans.ProjectSettingsStore;
import pro.sketchware.editor.ToastFactory;

@SuppressLint("StaticFieldLeak")
public abstract class BackgroundTask extends AsyncTask<Void, String, String> {

    public Context a;

    public BackgroundTask(Context var1) {
        a = var1;
    }

    @Override
    protected String doInBackground(Void... params) {
        try {
            if (isCancelled()) {
                return "";
            }
            b();
            return "";
        } catch (Exception e) {
            Log.e("BackgroundTask", e.getMessage(), e);
            // the bytecode's lying

            if (e instanceof ProjectSettingsStore) {
                return e.getMessage();
            }
            // getMessage() can be null (e.g. NPE); fall back to the full exception description.
            String detail = e.getMessage() != null ? e.getMessage() : String.valueOf(e);
            return a.getString(R.string.common_error_an_error_occurred) + "[" + detail + "]";
        }
    }

    public abstract void a();

    public abstract void a(String var1);

    public abstract void b() throws ProjectSettingsStore;

    @Override
    protected void onPostExecute(String result) {
        super.onPostExecute(result);
        if (result.isEmpty()) {
            a();
        } else {
            a(result);
            ToastFactory.b(a, result, 1).show();
        }
    }
}
