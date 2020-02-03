package com.nullparams.camera2api.activities;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import com.nullparams.camera2api.R;

public class InputKeyActivity extends AppCompatActivity {

    public static final String KEY = "key";
    private Button btnContinuar;
    private EditText txtChaveC;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_input_key);

        txtChaveC = findViewById(R.id.txtChaveC);
        btnContinuar = findViewById(R.id.btnContinuar);

        btnContinuar.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(txtChaveC.getText().toString().equals("") || txtChaveC.getText() == null){
                    Toast.makeText(getBaseContext(), "Insira sua chave para continuar", Toast.LENGTH_LONG).show();
                }else{
                    String chave = txtChaveC.getText().toString();
                    Intent intent = new Intent(getBaseContext(), FrontCameraActivity.class);
                    intent.putExtra("key", chave);
                    startActivity(intent);
                }
            }
        });
    }
}
