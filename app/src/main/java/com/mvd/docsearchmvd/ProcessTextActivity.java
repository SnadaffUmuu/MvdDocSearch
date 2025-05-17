package com.mvd.docsearchmvd;

import android.content.Intent;
import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

public class ProcessTextActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        CharSequence input = getIntent().getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT);
        String text = (input != null) ? input.toString() : "";

        Intent newIntent = new Intent(this, MainActivity.class);
        newIntent.setAction(Intent.ACTION_PROCESS_TEXT);
        newIntent.putExtra(Intent.EXTRA_PROCESS_TEXT, text);
        newIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); // важно: откроет в новом task
        newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP); // если Main уже был — перезапустит
        startActivity(newIntent);

        finish(); // Закрываем ProcessTextActivity
    }
}
