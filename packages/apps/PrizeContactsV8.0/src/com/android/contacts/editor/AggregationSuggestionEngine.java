/*
* Copyright (C) 2014 MediaTek Inc.
* Modification based on code covered by the mentioned copyright
* and/or permission notice(s).
*/
/*
 * Copyright (C) 2010 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.contacts.editor;

import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.os.Process;
import android.provider.ContactsContract.CommonDataKinds.Email;
import android.provider.ContactsContract.CommonDataKinds.Nickname;
import android.provider.ContactsContract.CommonDataKinds.Phone;
import android.provider.ContactsContract.CommonDataKinds.Photo;
import android.provider.ContactsContract.CommonDataKinds.StructuredName;
import android.provider.ContactsContract.Contacts;
import android.provider.ContactsContract.Contacts.AggregationSuggestions;
import android.provider.ContactsContract.Contacts.AggregationSuggestions.Builder;
import android.provider.ContactsContract.Data;
import android.provider.ContactsContract.RawContacts;
import android.text.TextUtils;

import com.android.contacts.common.model.ValuesDelta;
import com.android.contacts.compat.AggregationSuggestionsCompat;
import com.google.common.collect.Lists;
import com.mediatek.contacts.util.ContactsPortableUtils;
import com.mediatek.contacts.util.Log;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Runs asynchronous queries to obtain aggregation suggestions in the as-you-type mode.
 */
public class AggregationSuggestionEngine extends HandlerThread {
    public static final String TAG = "AggregationSuggestionEngine";

    public interface Listener {
        void onAggregationSuggestionChange();
    }

    public static final class RawContact {
        public long rawContactId;
        public String accountType;
        public String accountName;
        public String dataSet;

        @Override
        public String toString() {
            return "ID: " + rawContactId + " account: " + accountType + "/" + accountName
                    + " dataSet: " + dataSet;
        }
    }

    public static final class Suggestion {

        public long contactId;
        public long photoId;
        public String lookupKey;
        public String name;
        public String phoneNumber;
        public String emailAddress;
        public String nickname;
        public byte[] photo;
        public List<RawContact> rawContacts;

        @Override
        public String toString() {
            return "ID: " + contactId + " rawContacts: " + rawContacts + " name: " + name
            + " phone: " + phoneNumber + " email: " + emailAddress + " nickname: "
            + nickname + (photo != null ? " [has photo]" : "");
        }
    }

    private final class SuggestionContentObserver extends ContentObserver {
        private SuggestionContentObserver(Handler handler) {
            super(handler);
        }

        @Override
        public void onChange(boolean selfChange) {
            Log.d(TAG, "[onChange] selfChange =" + selfChange);
            scheduleSuggestionLookup();
        }
    }

    private static final int MESSAGE_RESET = 0;
    private static final int MESSAGE_NAME_CHANGE = 1;
    private static final int MESSAGE_DATA_CURSOR = 2;

    private static final long SUGGESTION_LOOKUP_DELAY_MILLIS = 300;

    private final Context mContext;

    private long[] mSuggestedContactIds = new long[0];

    private Handler mMainHandler;
    private Handler mHandler;
    private long mContactId;
    private Listener mListener;
    private Cursor mDataCursor;
    private ContentObserver mContentObserver;
    private Uri mSuggestionsUri;
    private int mSuggestionsLimit = 3;
    private boolean mPruneInvisibleContacts = true;

    public AggregationSuggestionEngine(Context context) {
        super("AggregationSuggestions", Process.THREAD_PRIORITY_BACKGROUND);
        mContext = context.getApplicationContext();
        mMainHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                AggregationSuggestionEngine.this.deliverNotification((Cursor) msg.obj);
            }
        };
    }

    protected Handler getHandler() {
        if (mHandler == null) {
            mHandler = new Handler(getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    AggregationSuggestionEngine.this.handleMessage(msg);
                }
            };
        }
        return mHandler;
    }

    public void setContactId(long contactId) {
        Log.d(TAG, "[setContactId] contactId = " + contactId
                + ", mContactId = " + mContactId);
        if (contactId != mContactId) {
            mContactId = contactId;
            reset();
        }
    }

    public void setSuggestionsLimit(int suggestionsLimit) {
        mSuggestionsLimit = suggestionsLimit;
    }

    public void setPruneInvisibleContacts (boolean pruneInvisibleContacts) {
        mPruneInvisibleContacts = pruneInvisibleContacts;
    }

    public void setListener(Listener listener) {
        mListener = listener;
    }

    @Override
    public boolean quit() {
        if (mDataCursor != null) {
            mDataCursor.close();
        }
        mDataCursor = null;
        if (mContentObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mContentObserver);
            mContentObserver = null;
        }
        return super.quit();
    }

    public void reset() {
        Handler handler = getHandler();
        handler.removeMessages(MESSAGE_NAME_CHANGE);
        handler.sendEmptyMessage(MESSAGE_RESET);
    }

    public void onNameChange(ValuesDelta values) {
        mSuggestionsUri = buildAggregationSuggestionUri(values);
        Log.d(TAG, "[onNameChange] after buildAggregationSuggestionUri,"
                + " mSuggestionsUri = "
                + (mSuggestionsUri == null ? "null" : mSuggestionsUri));
        if (mSuggestionsUri != null) {
            if (mContentObserver == null) {
                mContentObserver = new SuggestionContentObserver(getHandler());
                mContext.getContentResolver().registerContentObserver(
                        Contacts.CONTENT_URI, true, mContentObserver);
            }
        } else if (mContentObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mContentObserver);
            mContentObserver = null;
        }
        scheduleSuggestionLookup();
    }

    protected void scheduleSuggestionLookup() {
        Log.d(TAG, "[scheduleSuggestionLookup] mSuggestionsUri = "
                + (mSuggestionsUri == null ? "null" : mSuggestionsUri));

        Handler handler = getHandler();
        handler.removeMessages(MESSAGE_NAME_CHANGE);

        if (mSuggestionsUri == null) {
            return;
        }

        Message msg = handler.obtainMessage(MESSAGE_NAME_CHANGE, mSuggestionsUri);
        handler.sendMessageDelayed(msg, SUGGESTION_LOOKUP_DELAY_MILLIS);
    }

    private Uri buildAggregationSuggestionUri(ValuesDelta values) {
        StringBuilder nameSb = new StringBuilder();
        appendValue(nameSb, values, StructuredName.PREFIX);
        appendValue(nameSb, values, StructuredName.GIVEN_NAME);
        appendValue(nameSb, values, StructuredName.MIDDLE_NAME);
        appendValue(nameSb, values, StructuredName.FAMILY_NAME);
        appendValue(nameSb, values, StructuredName.SUFFIX);

        if (nameSb.length() == 0) {
            appendValue(nameSb, values, StructuredName.DISPLAY_NAME);
        }

        StringBuilder phoneticNameSb = new StringBuilder();
        appendValue(phoneticNameSb, values, StructuredName.PHONETIC_FAMILY_NAME);
        appendValue(phoneticNameSb, values, StructuredName.PHONETIC_MIDDLE_NAME);
        appendValue(phoneticNameSb, values, StructuredName.PHONETIC_GIVEN_NAME);

        if (nameSb.length() == 0 && phoneticNameSb.length() == 0) {
            return null;
        }

        // AggregationSuggestions.Builder() became visible in API level 23, so use it if applicable.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final Builder uriBuilder = new AggregationSuggestions.Builder()
                    .setLimit(mSuggestionsLimit)
                    .setContactId(mContactId);
            if (nameSb.length() != 0) {
                uriBuilder.addNameParameter(nameSb.toString());
            }
            if (phoneticNameSb.length() != 0) {
                uriBuilder.addNameParameter(phoneticNameSb.toString());
            }
            return uriBuilder.build();
        }

        // For previous SDKs, use the backup plan.
        final AggregationSuggestionsCompat.Builder uriBuilder =
                new AggregationSuggestionsCompat.Builder()
                .setLimit(mSuggestionsLimit)
                .setContactId(mContactId);
        if (nameSb.length() != 0) {
            uriBuilder.addNameParameter(nameSb.toString());
        }
        if (phoneticNameSb.length() != 0) {
            uriBuilder.addNameParameter(phoneticNameSb.toString());
        }
        return uriBuilder.build();
    }

    private void appendValue(StringBuilder sb, ValuesDelta values, String column) {
        String value = values.getAsString(column);
        if (!TextUtils.isEmpty(value)) {
            if (sb.length() > 0) {
                sb.append(' ');
            }
            sb.append(value);
        }
    }

    protected void handleMessage(Message msg) {
        switch (msg.what) {
            case MESSAGE_RESET:
                mSuggestedContactIds = new long[0];
                Log.d(TAG, "[handleMessage]reset mSuggestedContactIds and mDataCursor");
                /// M: [Google Issue]ALPS03277740 should also reset mDataCursor @{
                if (mDataCursor != null) {
                    mDataCursor.close();
                    mDataCursor = null;
                }
                /// @}
                break;
            case MESSAGE_NAME_CHANGE:
                loadAggregationSuggestions((Uri) msg.obj);
                break;
        }
    }

    private static final class DataQuery {

        public static final String SELECTION_PREFIX =
                Data.MIMETYPE + " IN ('"
                    + Phone.CONTENT_ITEM_TYPE + "','"
                    + Email.CONTENT_ITEM_TYPE + "','"
                    + StructuredName.CONTENT_ITEM_TYPE + "','"
                    + Nickname.CONTENT_ITEM_TYPE + "','"
                    + Photo.CONTENT_ITEM_TYPE + "')"
                + " AND " + Data.CONTACT_ID + " IN (";

        public static final String[] COLUMNS = {
            Data._ID,
            Data.CONTACT_ID,
            Data.LOOKUP_KEY,
            Data.PHOTO_ID,
            Data.DISPLAY_NAME,
            Data.RAW_CONTACT_ID,
            Data.MIMETYPE,
            Data.DATA1,
            Data.IS_SUPER_PRIMARY,
            Photo.PHOTO,
            RawContacts.ACCOUNT_TYPE,
            RawContacts.ACCOUNT_NAME,
            RawContacts.DATA_SET
        };

        public static final int ID = 0;
        public static final int CONTACT_ID = 1;
        public static final int LOOKUP_KEY = 2;
        public static final int PHOTO_ID = 3;
        public static final int DISPLAY_NAME = 4;
        public static final int RAW_CONTACT_ID = 5;
        public static final int MIMETYPE = 6;
        public static final int DATA1 = 7;
        public static final int IS_SUPERPRIMARY = 8;
        public static final int PHOTO = 9;
        public static final int ACCOUNT_TYPE = 10;
        public static final int ACCOUNT_NAME = 11;
        public static final int DATA_SET = 12;
    }

    private void loadAggregationSuggestions(Uri uri) {
        Log.d(TAG, "[loadAggregationSuggestions] uri = "
                + (uri == null ? "null" : uri));

        ContentResolver contentResolver = mContext.getContentResolver();
        Cursor cursor = contentResolver.query(uri, new String[]{Contacts._ID}, null, null, null);
        if (cursor == null) {
            return;
        }
        try {
            // If a new request is pending, chuck the result of the previous request
            if (getHandler().hasMessages(MESSAGE_NAME_CHANGE)) {
                return;
            }

            boolean changed = updateSuggestedContactIds(cursor);
            if (!changed) {
                return;
            }

            StringBuilder sb = new StringBuilder(DataQuery.SELECTION_PREFIX);
            int count = mSuggestedContactIds.length;
            for (int i = 0; i < count; i++) {
                if (i > 0) {
                    sb.append(',');
                }
                sb.append(mSuggestedContactIds[i]);
            }
            sb.append(')');
            /// M: Change feature, remove SIM/USIM contact on join popup window.
            if (ContactsPortableUtils.MTK_PHONE_BOOK_SUPPORT) {
                sb.append(" AND " + Contacts.INDICATE_PHONE_SIM + "=-1");
            }
            sb.toString();

            Cursor dataCursor = contentResolver.query(Data.CONTENT_URI,
                    DataQuery.COLUMNS, sb.toString(), null, Data.CONTACT_ID);
            if (dataCursor != null) {
                mMainHandler.sendMessage(mMainHandler.obtainMessage(MESSAGE_DATA_CURSOR, dataCursor));
            }
        } finally {
            cursor.close();
        }
    }

    private boolean updateSuggestedContactIds(final Cursor cursor) {
        Log.d(TAG, "[updateSuggestedContactIds] original mSuggestedContactIds = "
                + Arrays.toString(mSuggestedContactIds)
                + ", cursor.getCount() = " + cursor.getCount());
        final int count = cursor.getCount();
        boolean changed = count != mSuggestedContactIds.length;
        final ArrayList<Long> newIds = new ArrayList<Long>(count);
        while (cursor.moveToNext()) {
            final long contactId = cursor.getLong(0);
            if (!changed &&
                    Arrays.binarySearch(mSuggestedContactIds, contactId) < 0) {
                changed = true;
            }
            newIds.add(contactId);
        }

        if (changed) {
            mSuggestedContactIds = new long[newIds.size()];
            int i = 0;
            for (final Long newId : newIds) {
                mSuggestedContactIds[i++] = newId;
            }
            Arrays.sort(mSuggestedContactIds);
        }
        Log.d(TAG, "[updateSuggestedContactIds] updated mSuggestedContactIds = "
                + Arrays.toString(mSuggestedContactIds)
                + ", changed = " + changed);
        return changed;
    }

    protected void deliverNotification(Cursor dataCursor) {
        if (mDataCursor != null) {
            mDataCursor.close();
        }
        mDataCursor = dataCursor;
        if (mListener != null) {
            mListener.onAggregationSuggestionChange();
        }
    }

    public int getSuggestedContactCount() {
        return mDataCursor != null ? mDataCursor.getCount() : 0;
    }

    public List<Suggestion> getSuggestions() {
        final ArrayList<Long> visibleContacts = new ArrayList<>();
        if (mPruneInvisibleContacts) {
            final Uri contactFilterUri = Data.CONTENT_URI.buildUpon()
                    .appendQueryParameter(Data.VISIBLE_CONTACTS_ONLY, "true")
                    .build();
            final ContentResolver contentResolver = mContext.getContentResolver();
            final Cursor contactCursor = contentResolver.query(contactFilterUri,
                    new String[]{Data.CONTACT_ID}, null, null, null);
            try {
                if (contactCursor != null) {
                    while (contactCursor.moveToNext()) {
                        final long contactId = contactCursor.getLong(0);
                        visibleContacts.add(contactId);
                    }
                }
            } finally {
                contactCursor.close();
            }

        }

        ArrayList<Suggestion> list = Lists.newArrayList();
        if (mDataCursor != null) {
            Suggestion suggestion = null;
            long currentContactId = -1;
            mDataCursor.moveToPosition(-1);
            while (mDataCursor.moveToNext()) {
                long contactId = mDataCursor.getLong(DataQuery.CONTACT_ID);
                if (mPruneInvisibleContacts && !visibleContacts.contains(contactId)) {
                    continue;
                }
                if (contactId != currentContactId) {
                    suggestion = new Suggestion();
                    suggestion.contactId = contactId;
                    suggestion.name = mDataCursor.getString(DataQuery.DISPLAY_NAME);
                    suggestion.lookupKey = mDataCursor.getString(DataQuery.LOOKUP_KEY);
                    suggestion.rawContacts = Lists.newArrayList();
                    list.add(suggestion);
                    currentContactId = contactId;
                }

                long rawContactId = mDataCursor.getLong(DataQuery.RAW_CONTACT_ID);
                if (!containsRawContact(suggestion, rawContactId)) {
                    RawContact rawContact = new RawContact();
                    rawContact.rawContactId = rawContactId;
                    rawContact.accountName = mDataCursor.getString(DataQuery.ACCOUNT_NAME);
                    rawContact.accountType = mDataCursor.getString(DataQuery.ACCOUNT_TYPE);
                    rawContact.dataSet = mDataCursor.getString(DataQuery.DATA_SET);
                    suggestion.rawContacts.add(rawContact);
                }

                String mimetype = mDataCursor.getString(DataQuery.MIMETYPE);
                if (Phone.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    String data = mDataCursor.getString(DataQuery.DATA1);
                    int superprimary = mDataCursor.getInt(DataQuery.IS_SUPERPRIMARY);
                    if (!TextUtils.isEmpty(data)
                            && (superprimary != 0 || suggestion.phoneNumber == null)) {
                        suggestion.phoneNumber = data;
                    }
                } else if (Email.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    String data = mDataCursor.getString(DataQuery.DATA1);
                    int superprimary = mDataCursor.getInt(DataQuery.IS_SUPERPRIMARY);
                    if (!TextUtils.isEmpty(data)
                            && (superprimary != 0 || suggestion.emailAddress == null)) {
                        suggestion.emailAddress = data;
                    }
                } else if (Nickname.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    String data = mDataCursor.getString(DataQuery.DATA1);
                    if (!TextUtils.isEmpty(data)) {
                        suggestion.nickname = data;
                    }
                } else if (Photo.CONTENT_ITEM_TYPE.equals(mimetype)) {
                    long dataId = mDataCursor.getLong(DataQuery.ID);
                    long photoId = mDataCursor.getLong(DataQuery.PHOTO_ID);
                    if (dataId == photoId && !mDataCursor.isNull(DataQuery.PHOTO)) {
                        suggestion.photo = mDataCursor.getBlob(DataQuery.PHOTO);
                        suggestion.photoId = photoId;
                    }
                }
            }
        }
        return list;
    }

    public boolean containsRawContact(Suggestion suggestion, long rawContactId) {
        if (suggestion.rawContacts != null) {
            int count = suggestion.rawContacts.size();
            for (int i = 0; i < count; i++) {
                if (suggestion.rawContacts.get(i).rawContactId == rawContactId) {
                    return true;
                }
            }
        }
        return false;
    }
}
