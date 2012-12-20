/*
 * Copyright (C) 2012 Macadamian Technologies Inc.
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

package com.macadamian.mobile.android.monkeycam;

import com.macadamian.mobile.android.monkeycam.R;

import android.app.AlertDialog;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.widget.TextView;

//from: http://itkrauts.com/archives/26-Creating-a-simple-About-Dialog-in-Android-1.6.html
public class AboutDialogBuilder {

	public static AlertDialog create(Context context)
			throws NameNotFoundException {
		// Try to load the a package matching the name of our own package
		PackageInfo pInfo = context.getPackageManager().getPackageInfo(
				context.getPackageName(), PackageManager.GET_META_DATA);
		String versionInfo = pInfo.versionName;

		String aboutTitle = String.format("About %s",
				context.getString(R.string.app_name));
		String versionString = String.format("Version: %s", versionInfo);
		String aboutText = context.getString(R.string.about_text);

		// Set up the TextView
		final TextView message = new TextView(context);
		

		// Set some padding
		message.setPadding(5, 5, 5, 5);
		// Set up the final string
		message.setText(versionString + "\n\n" + aboutText);
	
		return new AlertDialog.Builder(context)
				.setTitle(aboutTitle)
				.setCancelable(true)
				.setIcon(R.drawable.icon)
				.setPositiveButton(context.getString(android.R.string.ok), null)
				.setView(message).create();
	}
}
