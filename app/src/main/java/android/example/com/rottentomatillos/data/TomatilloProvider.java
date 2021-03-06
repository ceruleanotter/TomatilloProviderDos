/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package android.example.com.rottentomatillos.data;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.sqlite.SQLiteConstraintException;
import android.database.sqlite.SQLiteDatabase;
import android.example.com.rottentomatillos.data.TomatilloContract.Movie;
import android.net.Uri;
import android.util.Log;

/**
 * This is a ContentProvider for the movie rating database. This content provider
 * works with {@link TomatilloContract} and {@link TomatilloDBHelper} to provide managed and secure
 * access to the movie database.
 */
public class TomatilloProvider extends ContentProvider {

    public static final String LOG_TAG = TomatilloProvider.class.getSimpleName();

    /**
     * This helps us create and gain access to the SQLiteDatabase.
     */
    private TomatilloDBHelper mDBHelper;

    // URI Matcher Codes
    private static final int MOVIE = 100;
    private static final int MOVIE_WITH_ID = 101;

    private static final UriMatcher sUriMatcher = buildUriMatcher();

    /**
     * Builds a UriMatcher object for the movie database URIs.
     */
    private static UriMatcher buildUriMatcher() {
        // All paths added to the UriMatcher have a corresponding code to return when a match is
        // found.  The code passed into the constructor represents the code to return for the root
        // URI.  It's common to use NO_MATCH as the code for this case.
        final UriMatcher matcher = new UriMatcher(UriMatcher.NO_MATCH);

        // For each type of URI you want to add, create a corresponding code. Note that UriMatchers
        // Need your content authority.
        matcher.addURI(TomatilloContract.CONTENT_AUTHORITY, Movie.TABLE_NAME, MOVIE);
        matcher.addURI(TomatilloContract.CONTENT_AUTHORITY, Movie.TABLE_NAME + "/#", MOVIE_WITH_ID);

        return matcher;
    }

    @Override
    public boolean onCreate() {
        mDBHelper = new TomatilloDBHelper(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
                        String sortOrder) {
        SQLiteDatabase db = mDBHelper.getReadableDatabase();

        // Get the constant integer representing the uri type and use in the switch statement.
        switch (sUriMatcher.match(uri)) {
            // Case where all movie ratings are selected
            case MOVIE: {
                Cursor cursor = db.query(
                        Movie.TABLE_NAME,
                        projection, selection, selectionArgs, null, null, sortOrder);
                return cursor;
            }
            // Case with only one movie rating selected, by ID
            case MOVIE_WITH_ID: {
                Cursor cursor = db.query(
                        Movie.TABLE_NAME,
                        projection,
                        Movie._ID + " = ?",
                        new String[]{String.valueOf(ContentUris.parseId(uri))},

                        null,
                        null,
                        sortOrder
                );
                return cursor;
            }
            default: {
                // In the default case, the uri must have been bad
                throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }
    }

    @Override
    public String getType(Uri uri) {
        switch (sUriMatcher.match(uri)) {
            case MOVIE: {
                return Movie.CONTENT_DIR_TYPE;
            }
            case MOVIE_WITH_ID: {
                return Movie.CONTENT_ITEM_TYPE;
            }
            default: {
                throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }
    }

    @Override
    public Uri insert(Uri uri, ContentValues contentValues) {
        checkInput(contentValues);

        switch (sUriMatcher.match(uri)) {
            case MOVIE: {
                long id = -1;
                // Insert the movie and catch the exception if it's already in the database.
                try {
                    id = mDBHelper.getWritableDatabase().insertOrThrow(
                            Movie.TABLE_NAME, null, contentValues);
                } catch (SQLiteConstraintException e) {
                    Log.i(LOG_TAG,
                            "Trying to insert " + contentValues.getAsString(Movie.TITLE) +
                                    " but it's already in the database."
                    );
                    // Do nothing if the movie is already there.
                }
                if (id == -1) return null; // it failed!
                // Only call if the insert succeeded. This statement notifies anything watching
                // that the data at this specific uri was changed.
                getContext().getContentResolver().notifyChange(uri, null);
                return ContentUris.withAppendedId(Movie.CONTENT_URI, id);
            }
            default: {
                throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }
    }

    @Override
    public int bulkInsert(Uri uri, ContentValues[] values) {
        final SQLiteDatabase db = mDBHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
        switch (match) {
            case MOVIE:

                // Allows you to issue multiple transactions and then have them executed in a batch
                db.beginTransaction();

                // Counts the number of inserts that are successful
                int numberInserted = 0;
                try {
                    for (ContentValues value : values) {
                        // Check the data is okay
                        checkInput(value);
                        // Try to insert
                        long id = -1;
                        try {
                            id = db.insertOrThrow(Movie.TABLE_NAME, null, value);
                        } catch (SQLiteConstraintException e) {
                            Log.i(LOG_TAG,
                                    "Trying to insert " + value.getAsString(Movie.TITLE) +
                                            " but it's already in the database."
                            );
                            // Do nothing if the movie is already there.
                        }
                        // As long as the insert didn't fail, increment the numberInserted
                        if (id != -1) {
                            numberInserted++;
                        }
                    }
                    // If you get to the end without an exception, set the transaction as successful
                    // No further database operations should be done after this call.
                    db.setTransactionSuccessful();
                } finally {
                    // Causes all of the issued transactions to occur at once
                    db.endTransaction();
                }
                if (numberInserted > 0) {
                    // Notifies the content resolver that the underlying data has changed
                    getContext().getContentResolver().notifyChange(uri, null);
                }
                return numberInserted;
            default:
                // The default case is not optimized
                return super.bulkInsert(uri, values);
        }
    }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mDBHelper.getWritableDatabase();
        final int match = sUriMatcher.match(uri);
        int numberDeleted;
        switch (match) {
            case MOVIE:
                numberDeleted = db.delete(
                        Movie.TABLE_NAME, selection, selectionArgs);
                break;
            case MOVIE_WITH_ID:
                numberDeleted = db.delete(
                        Movie.TABLE_NAME,
                        Movie._ID + " = ?",
                        new String[]{String.valueOf(ContentUris.parseId(uri))});
                break;
            default:
                throw new UnsupportedOperationException("Unknown uri: " + uri);
        }

        // The first condition works because a null deletes all rows
        if (selection == null || numberDeleted != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return numberDeleted;
    }

    @Override
    public int update(Uri uri, ContentValues contentValues, String selection, String[] selectionArgs) {
        final SQLiteDatabase db = mDBHelper.getWritableDatabase();
        int numberUpdated = 0;

        checkInput(contentValues);

        switch (sUriMatcher.match(uri)) {
            case MOVIE: {
                numberUpdated = db.update(
                        Movie.TABLE_NAME,
                        contentValues,
                        selection,
                        selectionArgs);
                break;
            }
            case MOVIE_WITH_ID: {
                numberUpdated = db.update(
                        Movie.TABLE_NAME,
                        contentValues,
                        Movie._ID + " = ?",
                        new String[]{String.valueOf(ContentUris.parseId(uri))}
                );
                break;
            }
            default: {
                throw new UnsupportedOperationException("Unknown uri: " + uri);
            }
        }
        if (numberUpdated != 0) {
            getContext().getContentResolver().notifyChange(uri, null);
        }
        return numberUpdated;
    }

    /**
     * Checks whether values can be inserted in the database. Throws IllegalArgumentException if:
     * 1. Values is null
     * 2. The rating is not between 1 and 5.
     */
    private static void checkInput(ContentValues values) {
        if (values == null) {
            throw new IllegalArgumentException("Cannot have null content values");
        }

        Integer rating = values.getAsInteger(Movie.RATING);

        if ((rating != null) && (rating.intValue() < 1 || rating.intValue() > 5)) {
            throw new IllegalArgumentException("The rating " +
                   rating + " is not between 1 and 5.");
        }
    }
}
