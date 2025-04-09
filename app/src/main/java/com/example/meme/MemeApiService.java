package com.example.meme;

import retrofit2.Call;
import retrofit2.http.Field;
import retrofit2.http.FormUrlEncoded;
import retrofit2.http.Header;
import retrofit2.http.POST;

public interface MemeApiService {
    @FormUrlEncoded
    @POST("api/meme-generation")
    Call<MemeResponse> generateMeme(
            @Header("api-key") String apiKey,
            @Field("text") String text,
            @Field("image") String imageBase64
    );
}