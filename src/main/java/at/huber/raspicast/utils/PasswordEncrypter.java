/*
 * Copyright (C) 2020  Benjamin Huber
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package at.huber.raspicast.utils;

import java.nio.charset.Charset;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import android.util.Base64;

import at.huber.raspicast.BuildConfig;

public class PasswordEncrypter {

	public static String encrypt(String value) {

		if (value != null && !value.equals("")) {
			byte[] raw = BuildConfig.KEY_FOR_ENCRYPTION.getBytes(Charset.forName("UTF-8"));

			SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");
			Cipher cipher;
			try {
				cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				cipher.init(Cipher.ENCRYPT_MODE, skeySpec, new IvParameterSpec(new byte[16]));
				byte[] encrypted = cipher.doFinal(value.getBytes(Charset.forName("UTF-8")));
				return Base64.encodeToString(encrypted, Base64.DEFAULT);
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		} else {
			return null;
		}

	}

	public static String decrypt(String encryptedPasswordStringBase64) {

		if (encryptedPasswordStringBase64 != null && !encryptedPasswordStringBase64.equals("")) {
			byte[] encrypted = Base64.decode(encryptedPasswordStringBase64, Base64.DEFAULT);

			byte[] raw = BuildConfig.KEY_FOR_ENCRYPTION.getBytes(Charset.forName("UTF-8"));
			SecretKeySpec skeySpec = new SecretKeySpec(raw, "AES");

			try {
				Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
				cipher.init(Cipher.DECRYPT_MODE, skeySpec, new IvParameterSpec(new byte[16]));
				byte[] original = cipher.doFinal(encrypted);

				return new String(original, Charset.forName("UTF-8"));
			} catch (Exception e) {
				e.printStackTrace();
				return null;
			}
		} else {
			return null;
		}
	}
}
