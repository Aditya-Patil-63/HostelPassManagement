package com.shirpur.hostelpassmanagement.helpers;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.widget.ImageView;
import android.content.Context;

import com.bumptech.glide.Glide;
import com.shirpur.hostelpassmanagement.R;

import java.io.ByteArrayOutputStream;

/**
 * Utility for encoding/decoding profile photos as Base64 strings.
 * This stores photos directly in Firestore, eliminating the need for Firebase Storage.
 */
public class PhotoHelper {

    /**
     * Reads a Uri directly and fixes Exif rotation from camera intent headers.
     */
    public static Bitmap getBitmapFromUri(android.content.Context context, android.net.Uri uri) {
        try {
            // Read orientation
            java.io.InputStream exifStream = context.getContentResolver().openInputStream(uri);
            android.media.ExifInterface exif = new android.media.ExifInterface(exifStream);
            int orientation = exif.getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL);
            exifStream.close();

            int rotationDegrees = 0;
            switch (orientation) {
                case android.media.ExifInterface.ORIENTATION_ROTATE_90: rotationDegrees = 90; break;
                case android.media.ExifInterface.ORIENTATION_ROTATE_180: rotationDegrees = 180; break;
                case android.media.ExifInterface.ORIENTATION_ROTATE_270: rotationDegrees = 270; break;
            }

            // Read bitmap
            java.io.InputStream imgStream = context.getContentResolver().openInputStream(uri);
            Bitmap bitmap = BitmapFactory.decodeStream(imgStream);
            imgStream.close();

            if (bitmap != null && rotationDegrees != 0) {
                android.graphics.Matrix matrix = new android.graphics.Matrix();
                matrix.preRotate(rotationDegrees);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }

            return bitmap;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Converts a Bitmap to a compressed Base64 string.
     * The bitmap is resized to max 400x400 and compressed at 60% JPEG quality
     * to keep the Firestore document size well under the 1MB limit.
     */
    public static String bitmapToBase64(Bitmap bitmap) {
        if (bitmap == null) return "";

        // Resize to keep Firestore document small
        int maxSize = 400;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width > maxSize || height > maxSize) {
            float ratio = (float) width / (float) height;
            if (ratio > 1) {
                width = maxSize;
                height = (int) (width / ratio);
            } else {
                height = maxSize;
                width = (int) (height * ratio);
            }
            bitmap = Bitmap.createScaledBitmap(bitmap, width, height, true);
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 60, baos);
        byte[] bytes = baos.toByteArray();
        return Base64.encodeToString(bytes, Base64.DEFAULT);
    }

    /**
     * Converts a Base64 string back to a Bitmap.
     */
    public static Bitmap base64ToBitmap(String base64) {
        if (base64 == null || base64.isEmpty()) return null;
        try {
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Loads a photo (Base64 string) into an ImageView.
     * Falls back to avatar_placeholder if the string is empty or invalid.
     */
    public static void loadPhoto(String photoData, ImageView imageView) {
        if (photoData != null && !photoData.isEmpty()) {
            Bitmap bitmap = base64ToBitmap(photoData);
            if (bitmap != null) {
                imageView.setImageBitmap(bitmap);
                return;
            }
        }
        imageView.setImageResource(R.drawable.avatar_placeholder);
    }

    /**
     * Loads a photo URL using Glide.
     * Falls back to Base64 decoding if the data is not a URL, maintaining backward compatibility.
     */
    public static void loadWithGlide(Context context, String photoData, ImageView imageView) {
        if (photoData != null && !photoData.isEmpty()) {
            if (photoData.startsWith("http://") || photoData.startsWith("https://")) {
                Glide.with(context)
                        .load(photoData)
                        .placeholder(R.drawable.avatar_placeholder)
                        .error(R.drawable.avatar_placeholder)
                        .into(imageView);
                return;
            } else {
                // Fallback to Base64
                Bitmap bitmap = base64ToBitmap(photoData);
                if (bitmap != null) {
                    imageView.setImageBitmap(bitmap);
                    return;
                }
            }
        }
        imageView.setImageResource(R.drawable.avatar_placeholder);
    }
}
