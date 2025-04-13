package com.example.meme;

import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.http.Body;
import retrofit2.http.Header;
import retrofit2.http.POST;
import com.google.gson.JsonObject;

public interface OpenRouterApi {
    @POST("api/v1/chat/completions")
    Call<JsonObject> generateMeme(  // Изменили возвращаемый тип на JsonObject
                                    @Header("Authorization") String authHeader,
                                    @Header("Content-Type") String contentType,
                                    @Body RequestBody requestBody
    );
}