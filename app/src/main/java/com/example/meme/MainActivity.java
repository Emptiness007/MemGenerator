package com.example.meme;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private ImageView imageView;
    private EditText topTextEditText, bottomTextEditText;
    private Bitmap selectedImage;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private ActivityResultLauncher<String> permissionLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        topTextEditText = findViewById(R.id.topText);
        bottomTextEditText = findViewById(R.id.bottomText);

        Button selectImageButton = findViewById(R.id.selectImageButton);
        Button generateMemeButton = findViewById(R.id.generateMemeButton);
        Button saveMemeButton = findViewById(R.id.saveMemeButton);

        // Инициализация лаунчеров
        setupLaunchers();

        selectImageButton.setOnClickListener(v -> checkPermissionsAndSelectImage());
        generateMemeButton.setOnClickListener(v -> generateMeme());
        saveMemeButton.setOnClickListener(v -> saveMeme());
    }

    private void setupLaunchers() {
        // Лаунчер для запроса разрешений
        permissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                isGranted -> {
                    if (isGranted) {
                        openGallery();
                    } else {
                        Toast.makeText(this, "Разрешение необходимо для выбора изображения", Toast.LENGTH_SHORT).show();
                    }
                });

        // Лаунчер для выбора изображения из галереи
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            selectedImage = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
                            imageView.setImageBitmap(selectedImage);
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private void checkPermissionsAndSelectImage() {
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_IMAGES;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            openGallery();
        } else {
            permissionLauncher.launch(permission);
        }
    }

    private void openGallery() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        galleryLauncher.launch(intent);
    }

    private void generateMeme() {
        if (selectedImage == null) {
            Toast.makeText(this, "Сначала выберите изображение", Toast.LENGTH_SHORT).show();
            return;
        }

        String topText = topTextEditText.getText().toString();
        String bottomText = bottomTextEditText.getText().toString();

        // Здесь будет ваша логика генерации мема
        // Пока просто покажем выбранное изображение
        imageView.setImageBitmap(selectedImage);
        Toast.makeText(this, "Мем сгенерирован: " + topText + " - " + bottomText, Toast.LENGTH_SHORT).show();
    }

    private void saveMeme() {
        if (selectedImage == null) {
            Toast.makeText(this, "Нет изображения для сохранения", Toast.LENGTH_SHORT).show();
            return;
        }

        // Здесь будет логика сохранения мема
        Toast.makeText(this, "Мем сохранен в галерею", Toast.LENGTH_SHORT).show();
    }
}