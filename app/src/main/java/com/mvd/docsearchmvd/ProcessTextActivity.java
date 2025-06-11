package com.mvd.docsearchmvd;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class ProcessTextActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent sourceIntent = getIntent();
        String action = sourceIntent.getAction();
        String type = sourceIntent.getType();

        String text = "";

        if (Intent.ACTION_PROCESS_TEXT.equals(action)) {
            CharSequence input = sourceIntent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
            if (input != null) text = input.toString();

        } else if (Intent.ACTION_SEND.equals(action) && "text/plain".equals(type)) {
            CharSequence input = sourceIntent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            if (input != null) text = input.toString();
        }

        Intent newIntent = new Intent(this, MainActivity.class);
        newIntent.setAction(Intent.ACTION_PROCESS_TEXT);
        newIntent.putExtra(Intent.EXTRA_PROCESS_TEXT, text);
        newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(newIntent);

        finish();
    }
}
