package com.cooleval.speech;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.cooleval.audiorecording.R;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.RequestParams;

import cz.msebera.android.httpclient.Header;

import org.apache.commons.io.IOUtils;

import java.io.*;

public class SpeechRecognitionActivity extends Activity {
  private static final String AUDIO_RECORDER_FOLDER = "AudioRecorder";

  String[] langName = new String[]{"汉语", "英语"};
  String[] langCode = new String[]{"zh_CN", "en_US"};
  int currentFormat = 1;

  Thread recordingThread;
  boolean isRecording = false;
  boolean isWritingFile = false;

  String recordFilename;

  TextView resultView = null;

  int audioSource = MediaRecorder.AudioSource.MIC;
  int sampleRateInHz = 16000;
  int channelConfig = AudioFormat.CHANNEL_IN_MONO;
  int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
  int bufferSizeInBytes = AudioRecord.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);

  AudioRecord audioRecorder = null;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.main);

    setButtonHandlers();
    enableButtons(false);
    setLanguageButtonCaption();
    resultView = (TextView) findViewById(R.id.result);
  }

  private void setButtonHandlers() {
    ((Button) findViewById(R.id.btnStart)).setOnClickListener(btnClick);
    ((Button) findViewById(R.id.btnStop)).setOnClickListener(btnClick);
    ((Button) findViewById(R.id.btnFormat)).setOnClickListener(btnClick);
  }

  private void enableButton(int id, boolean isEnable) {
    ((Button) findViewById(id)).setEnabled(isEnable);
  }

  private void enableButtons(boolean isRecording) {
    enableButton(R.id.btnStart, !isRecording);
    enableButton(R.id.btnFormat, !isRecording);
    enableButton(R.id.btnStop, isRecording);
  }

  private void setLanguageButtonCaption() {
    ((Button) findViewById(R.id.btnFormat))
      .setText(getString(R.string.audio_format) + " ("
        + langName[currentFormat] + ")");
  }

  private String getFilename() {
    String filepath = Environment.getExternalStorageDirectory().getPath();
    File file = new File(filepath, AUDIO_RECORDER_FOLDER);

    if (!file.exists()) {
      file.mkdirs();
    }

    return (file.getAbsolutePath() + "/" + System.currentTimeMillis() + ".wav");
  }

  private void startRecording() {

    if (null == audioRecorder) {
      if (bufferSizeInBytes == AudioRecord.ERROR || bufferSizeInBytes == AudioRecord.ERROR_BAD_VALUE) {
        bufferSizeInBytes = sampleRateInHz * 2;
      }
      audioRecorder = new AudioRecord(audioSource,
        sampleRateInHz,
        channelConfig,
        audioFormat,
        bufferSizeInBytes);
    }

    resultView.setText(null);
    audioRecorder.startRecording();
    isRecording = true;

    recordingThread = new Thread(new Runnable() {

      public void run() {
        //String filepath = Environment.getExternalStorageDirectory().getPath();
        BufferedOutputStream os = null;
        try {
          recordFilename = getFilename();
          os = new BufferedOutputStream(new FileOutputStream(recordFilename));
          isWritingFile = true;

          byte[] audioBuffer = new byte[bufferSizeInBytes];

          while (isRecording) {
            //从MIC保存数据到缓冲区
            int bufferReadResult = audioRecorder.read(audioBuffer, 0,
              bufferSizeInBytes);

            byte[] tmpBuf = new byte[bufferReadResult];
            System.arraycopy(audioBuffer, 0, tmpBuf, 0, bufferReadResult);
            //写入数据
            os.write(tmpBuf, 0, tmpBuf.length);
          }
          os.flush();
          os.close();
          isWritingFile = false;
        } catch (Throwable e) {
          Toast.makeText(SpeechRecognitionActivity.this, e.getMessage(), Toast.LENGTH_SHORT);
        }
      }
    });
    recordingThread.start();

  }

  private void stopRecording() {
    if (null != audioRecorder) {
      isRecording = false;
      audioRecorder.stop();
      audioRecorder.release();
      audioRecorder = null;
      recordingThread = null;

      byte[] myByteArray = new byte[0];
      try {
        myByteArray =
          IOUtils.toByteArray(new FileInputStream(recordFilename));
      } catch (IOException ioe) {

      }
      RequestParams params = new RequestParams();
      params.put("speech", new ByteArrayInputStream(myByteArray), "speech.wmv");
      AsyncHttpClient client = new AsyncHttpClient();
      client.setConnectTimeout(2000);
      client.post("http://104.199.222.30:8080/recognition/" + langCode[currentFormat] + "/0/ANDROID", params, new AsyncHttpResponseHandler() {

        @Override
        public void onStart() {
          // called before request is started
        }

        @Override
        public void onSuccess(int statusCode, Header[] headers, byte[] response) {
          // called when response HTTP status is "200 OK"
          String result = new String(response);
          resultView.setText("结果: " + result);

        }

        @Override
        public void onFailure(int statusCode, Header[] headers, byte[] errorResponse, Throwable e) {
          // called when response HTTP status is "4XX" (eg. 401, 403, 404)
          resultView.setText("错误: " + e.getMessage());
          ;
        }

        @Override
        public void onRetry(int retryNo) {
          // called when request is retried
        }
      });
    }
  }

  private void displayFormatDialog() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    String formats[] = {"汉语", "英语"};

    builder.setTitle(getString(R.string.choose_format_title))
      .setSingleChoiceItems(formats, currentFormat,
        new DialogInterface.OnClickListener() {

          @Override
          public void onClick(DialogInterface dialog,
                              int which) {
            currentFormat = which;
            setLanguageButtonCaption();

            dialog.dismiss();
          }
        }).show();
  }

  private MediaRecorder.OnErrorListener errorListener = new MediaRecorder.OnErrorListener() {
    @Override
    public void onError(MediaRecorder mr, int what, int extra) {
      Toast.makeText(SpeechRecognitionActivity.this,
        "Error: " + what + ", " + extra, Toast.LENGTH_SHORT).show();
    }
  };

  private MediaRecorder.OnInfoListener infoListener = new MediaRecorder.OnInfoListener() {
    @Override
    public void onInfo(MediaRecorder mr, int what, int extra) {
      Toast.makeText(SpeechRecognitionActivity.this,
        "Warning: " + what + ", " + extra, Toast.LENGTH_SHORT)
        .show();
    }
  };

  private View.OnClickListener btnClick = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      switch (v.getId()) {
        case R.id.btnStart: {
          Toast.makeText(SpeechRecognitionActivity.this, getResources().getString(R.string.stop_clicked),
            Toast.LENGTH_SHORT).show();

          enableButtons(true);
          startRecording();

          break;
        }
        case R.id.btnStop: {
          Toast.makeText(SpeechRecognitionActivity.this, "结束录音",
            Toast.LENGTH_SHORT).show();
          enableButtons(false);
          stopRecording();

          break;
        }
        case R.id.btnFormat: {
          displayFormatDialog();

          break;
        }
      }
    }
  };
}
