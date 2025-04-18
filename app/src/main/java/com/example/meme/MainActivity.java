package com.example.meme;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.FileProvider;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import okhttp3.MediaType;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.Headers;
import retrofit2.http.POST;

public class MainActivity extends AppCompatActivity {

    private ImageView imageView;
    private Bitmap selectedImage, generatedMeme;
    private ActivityResultLauncher<Intent> galleryLauncher;
    private FloatingActionButton fabMenu, fabShare, fabSave;
    private MaterialButton selectImageButton, generateMemeButton;
    private ProgressBar loadingProgressBar;
    private boolean isMenuOpen = false;
    private static final String OPENROUTER_API_KEY = "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";
    private static final String OPENROUTER_API_URL = "https://openrouter.ai/api/v1/";
    private static final String TAG = "MainActivity";
    private static final int MAX_RETRIES = 3;
    private int currentRetry = 0;
    private static final int MAX_IMAGE_DIMENSION = 1024;
    private static final int JPEG_QUALITY = 70;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        imageView = findViewById(R.id.imageView);
        selectImageButton = findViewById(R.id.selectImageButton);
        generateMemeButton = findViewById(R.id.generateMemeButton);
        fabMenu = findViewById(R.id.fabMenu);
        fabShare = findViewById(R.id.fabShare);
        fabSave = findViewById(R.id.fabSave);
        loadingProgressBar = findViewById(R.id.loadingProgressBar);

        setupGalleryLauncher();

        selectImageButton.setOnClickListener(v -> openGallery());
        generateMemeButton.setOnClickListener(v -> generateMeme());
        fabMenu.setOnClickListener(v -> toggleMenu());
        fabShare.setOnClickListener(v -> shareMeme());
        fabSave.setOnClickListener(v -> saveMeme());
    }

    public interface OpenRouterApi {
        @POST("chat/completions")
        Call<JsonObject> generateMeme(
                @Header("Authorization") String authorization,
                @Header("Content-Type") String contentType,
                @Body RequestBody body
        );
    }
    private void setupGalleryLauncher() {
        galleryLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                        Uri imageUri = result.getData().getData();
                        try {
                            selectedImage = loadBitmapWithOrientation(imageUri);
                            imageView.setImageBitmap(selectedImage);
                            generatedMeme = null;
                            hideMenu();
                            Log.d(TAG, "Image loaded: " + selectedImage.getWidth() + "x" + selectedImage.getHeight());
                        } catch (IOException e) {
                            e.printStackTrace();
                            Toast.makeText(this, "Ошибка загрузки изображения", Toast.LENGTH_SHORT).show();
                        }
                    }
                });
    }

    private Bitmap loadBitmapWithOrientation(Uri imageUri) throws IOException {
        Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), imageUri);
        InputStream inputStream = getContentResolver().openInputStream(imageUri);
        ExifInterface exif = new ExifInterface(inputStream);
        int orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL);
        inputStream.close();

        Matrix matrix = new Matrix();
        switch (orientation) {
            case ExifInterface.ORIENTATION_ROTATE_90:
                matrix.postRotate(90);
                break;
            case ExifInterface.ORIENTATION_ROTATE_180:
                matrix.postRotate(180);
                break;
            case ExifInterface.ORIENTATION_ROTATE_270:
                matrix.postRotate(270);
                break;
            default:
                return bitmap;
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
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

        showLoading(true);
        currentRetry = 0;
        generateMemeWithRetry();
    }

    private void generateMemeWithRetry() {
        Bitmap resizedBitmap = resizeBitmap(selectedImage);
        String base64Image = bitmapToBase64(resizedBitmap);

        Log.d(TAG, "Base64 length: " + base64Image.length() + " characters");

        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(OPENROUTER_API_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        String jsonBody = "{"
                + "\"model\": \"google/gemini-2.0-flash-exp:free\","
                + "\"messages\": [{"
                + "\"role\": \"user\","
                + "\"content\": ["
                + "{\"type\": \"text\", \"text\": \"Generate a short and super funny meme caption for this image (max 5-7 words). Return only the caption text without any additional formatting or explanations.\"},"
                + "{\"type\": \"image_url\", \"image_url\": {\"url\": \"data:image/jpeg;base64," + base64Image + "\"}}"
                + "]"
                + "}]"
                + "}";

        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), jsonBody);

        OpenRouterApi api = retrofit.create(OpenRouterApi.class);
        Call<JsonObject> call = api.generateMeme("Bearer " + OPENROUTER_API_KEY, "application/json", requestBody);

        call.enqueue(new retrofit2.Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JsonObject jsonObject = response.body();
                        Log.d(TAG, "Full API Response: " + jsonObject.toString());

                        JsonArray choices = jsonObject.getAsJsonArray("choices");
                        if (choices != null && choices.size() > 0) {
                            JsonObject firstChoice = choices.get(0).getAsJsonObject();
                            JsonObject message = firstChoice.getAsJsonObject("message");
                            String memeText = message.get("content").getAsString().trim();

                            // Упрощенная обработка ответа
                            String selectedCaption = memeText;

                            // Удаляем кавычки если они есть
                            if (selectedCaption.startsWith("\"") && selectedCaption.endsWith("\"")) {
                                selectedCaption = selectedCaption.substring(1, selectedCaption.length() - 1);
                            }

                            // Удаляем звездочки и другие форматирования
                            selectedCaption = selectedCaption.replace("*", "").trim();

                            if (!selectedCaption.isEmpty()) {
                                Log.d(TAG, "Selected Caption: " + selectedCaption);
                                translateToRussian(selectedCaption);
                            } else {
                                handleGenerationError("Caption is empty");
                            }
                        } else {
                            handleGenerationError("Choices array is null or empty");
                        }
                    } catch (Exception e) {
                        handleGenerationError("Error processing response: " + e.getMessage());
                    }
                } else {
                    try {
                        String errorBody = response.errorBody() != null ? response.errorBody().string() : "Unknown error";
                        handleGenerationError("API Error - Code: " + response.code() + ", Body: " + errorBody);
                    } catch (IOException e) {
                        handleGenerationError("Error reading error body: " + e.getMessage());
                    }
                }
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                handleGenerationError("Network Error: " + t.getMessage());
            }
        });
    }

    private Bitmap resizeBitmap(Bitmap original) {
        int width = original.getWidth();
        int height = original.getHeight();

        float scale = Math.min((float) MAX_IMAGE_DIMENSION / width,
                (float) MAX_IMAGE_DIMENSION / height);

        Matrix matrix = new Matrix();
        matrix.postScale(scale, scale);

        return Bitmap.createBitmap(original, 0, 0, width, height, matrix, true);
    }

    private void handleGenerationError(String errorMessage) {
        Log.e(TAG, errorMessage);

        if (currentRetry < MAX_RETRIES) {
            currentRetry++;
            new Handler().postDelayed(() -> generateMemeWithRetry(), 1000);
        } else {
            runOnUiThread(() -> {
                showLoading(false);
                Toast.makeText(MainActivity.this, "Не удалось сгенерировать подпись", Toast.LENGTH_SHORT).show();
            });
        }
    }

    private void translateToRussian(String text) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(OPENROUTER_API_URL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        String jsonBody = "{"
                + "\"model\": \"mistralai/mistral-nemo:free\","
                + "\"messages\": [{"
                + "\"role\": \"user\","
                + "\"content\": \"Translate this to Russian (return only the translation without explanations, notes and punctuation): " + text.replace("\"", "\\\"") + "\""
                + "}]"
                + "}";

        RequestBody requestBody = RequestBody.create(MediaType.parse("application/json"), jsonBody);

        OpenRouterApi api = retrofit.create(OpenRouterApi.class);
        Call<JsonObject> call = api.generateMeme("Bearer " + OPENROUTER_API_KEY, "application/json", requestBody);

        call.enqueue(new retrofit2.Callback<JsonObject>() {
            @Override
            public void onResponse(Call<JsonObject> call, Response<JsonObject> response) {
                String translatedText = text; // Используем оригинальный текст по умолчанию

                if (response.isSuccessful() && response.body() != null) {
                    try {
                        JsonObject jsonObject = response.body();
                        Log.d(TAG, "Translation API Response: " + jsonObject.toString());

                        JsonArray choices = jsonObject.getAsJsonArray("choices");
                        if (choices != null && choices.size() > 0) {
                            JsonObject firstChoice = choices.get(0).getAsJsonObject();
                            JsonObject message = firstChoice.getAsJsonObject("message");
                            String fullTranslation = message.get("content").getAsString().trim();

                            // Берем только текст до первой точки
                            int dotIndex = fullTranslation.indexOf('.');
                            if (dotIndex != -1) {
                                translatedText = fullTranslation.substring(0, dotIndex).trim();
                            } else {
                                translatedText = fullTranslation;
                            }

                            // Удаляем скобки и текст в них
                            translatedText = translatedText.replaceAll("\\(.*?\\)", "").trim();

                            // Удаляем кавычки если есть
                            if (translatedText.startsWith("\"") && translatedText.endsWith("\"")) {
                                translatedText = translatedText.substring(1, translatedText.length() - 1).trim();
                            }

                            Log.d(TAG, "Final Translated Caption: " + translatedText);
                        }
                    } catch (Exception e) {
                        Log.e(TAG, "Error processing translation: " + e.getMessage());
                    }
                }

                createMeme(translatedText);
            }

            @Override
            public void onFailure(Call<JsonObject> call, Throwable t) {
                Log.e(TAG, "Translation Network Error: " + t.getMessage());
                createMeme(text);
            }
        });
    }

    private String bitmapToBase64(Bitmap bitmap) {
        Bitmap compressedBitmap = compressBitmap(bitmap);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        compressedBitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, baos);
        byte[] byteArray = baos.toByteArray();
        return Base64.encodeToString(byteArray, Base64.NO_WRAP);
    }

    private Bitmap compressBitmap(Bitmap original) {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inSampleSize = 2;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        original.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out);
        byte[] byteArray = out.toByteArray();
        return BitmapFactory.decodeByteArray(byteArray, 0, byteArray.length, options);
    }

    private void createMeme(String text) {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    // 1. Проверяем и подготавливаем изображение
                    if (selectedImage == null || selectedImage.isRecycled()) {
                        Toast.makeText(MainActivity.this, "Ошибка: изображение не доступно", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 2. Создаем mutable копию изображения
                    generatedMeme = selectedImage.copy(Bitmap.Config.ARGB_8888, true);
                    if (generatedMeme == null) {
                        Toast.makeText(MainActivity.this, "Ошибка создания мема", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // 3. Создаем Canvas и Paint для текста
                    Canvas canvas = new Canvas(generatedMeme);

                    // Настройки для основного текста
                    Paint textPaint = new Paint();
                    textPaint.setColor(Color.WHITE);
                    textPaint.setTextSize(120); // Увеличиваем размер шрифта
                    textPaint.setTextAlign(Paint.Align.CENTER);
                    textPaint.setAntiAlias(true);

                    // Настройки для обводки текста
                    Paint strokePaint = new Paint();
                    strokePaint.setColor(Color.BLACK);
                    strokePaint.setTextSize(100);
                    strokePaint.setTextAlign(Paint.Align.CENTER);
                    strokePaint.setStyle(Paint.Style.STROKE);
                    strokePaint.setStrokeWidth(6); // Толщина обводки
                    strokePaint.setAntiAlias(true);

                    // 4. Позиционируем текст внизу изображения
                    int x = generatedMeme.getWidth() / 2;
                    int y = generatedMeme.getHeight() - 50; // 50px от нижнего края

                    // 5. Рисуем текст (сначала обводку, потом основной текст)
                    canvas.drawText(text, x, y, strokePaint);
                    canvas.drawText(text, x, y, textPaint);

                    // 6. Обновляем ImageView
                    imageView.setImageBitmap(generatedMeme);
                    imageView.invalidate();

                    // 7. Скрываем ProgressBar и показываем меню
                    showLoading(false);
                    showMenu();

                    Toast.makeText(MainActivity.this, "Мем сгенерирован!", Toast.LENGTH_SHORT).show();

                } catch (Exception e) {
                    Log.e(TAG, "Error creating meme: ", e);
                    showLoading(false);
                    Toast.makeText(MainActivity.this, "Ошибка создания мема", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showLoading(boolean show) {
        loadingProgressBar.setVisibility(show ? View.VISIBLE : View.GONE);
        selectImageButton.setEnabled(!show);
        generateMemeButton.setEnabled(!show);
    }

    private void toggleMenu() {
        if (isMenuOpen) {
            hideMenu();
        } else {
            showMenu();
        }
        isMenuOpen = !isMenuOpen;
    }

    private void showMenu() {
        fabMenu.setVisibility(View.VISIBLE);
        fabShare.setVisibility(View.VISIBLE);
        fabSave.setVisibility(View.VISIBLE);
        fabShare.animate().translationY(-getResources().getDimension(R.dimen.fab_margin) * 0.5f);
        fabSave.animate().translationY(-getResources().getDimension(R.dimen.fab_margin) * 0.5f);
    }

    private void hideMenu() {
        fabShare.animate().translationY(0);
        fabSave.animate().translationY(0).withEndAction(() -> {
            fabMenu.setVisibility(View.GONE);
            fabShare.setVisibility(View.GONE);
            fabSave.setVisibility(View.GONE);
        });
    }

    private void shareMeme() {
        if (generatedMeme == null || generatedMeme.isRecycled()) {
            Toast.makeText(this, "Нет мема для отправки", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "generatedMeme is null or recycled");
            return;
        }

        try {
            // Сохраняем мем в файл
            File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "shared_meme.png");
            FileOutputStream fos = new FileOutputStream(file);
            generatedMeme.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.flush();
            fos.close();
            Log.d(TAG, "Meme saved to: " + file.getAbsolutePath());

            // Проверяем, существует ли файл и его размер
            if (file.exists() && file.length() > 0) {
                Uri uri = FileProvider.getUriForFile(this, "com.example.meme.fileprovider", file);
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("image/png");
                shareIntent.putExtra(Intent.EXTRA_STREAM, uri);
                shareIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                startActivity(Intent.createChooser(shareIntent, "Поделиться мемом"));
            } else {
                Toast.makeText(this, "Ошибка: файл мема пустой", Toast.LENGTH_SHORT).show();
                Log.e(TAG, "Meme file is empty or not created");
            }
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка при отправке", Toast.LENGTH_SHORT).show();
            Log.e(TAG, "Error sharing meme: " + e.getMessage());
        }
    }

    private void saveMeme() {
        if (generatedMeme == null) {
            Toast.makeText(this, "Нет мема для сохранения", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String fileName = "Meme_" + timeStamp + ".png";
            File file = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), fileName);

            FileOutputStream fos = new FileOutputStream(file);
            generatedMeme.compress(Bitmap.CompressFormat.PNG, 100, fos);
            fos.close();

            MediaStore.Images.Media.insertImage(getContentResolver(), file.getAbsolutePath(), fileName, "Generated Meme");
            Toast.makeText(this, "Мем сохранен в галерею", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Ошибка сохранения", Toast.LENGTH_SHORT).show();
        }
    }
}