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
package at.huber.raspicast;

public class Constants {
	
	public static final boolean LIGHT_THEME_DEFAULT=true;
	
	public static final String PREF_FILE_NAME = "raspiIpTvSharedPrefs";

	public static final String PREF_INPUT_DIR_NAME = "InputDir";
	public static final String PREF_LAST_PLAYED_STREAM_MAN_NAME = "lastPlayedManually";
	public static final String PREF_LOOP_STREAM = "loopStream";

	public static final String PREF_HOSTNAME = "hostname";
	public static final String PREF_PORT = "port";
	public static final String PREF_USER = "user";
	public static final String PREF_PASSWORD = "password";
	public static final String PREF_KEYFILE_PATH = "keyFilePath";
	public static final String PREF_SSH_FINGERPRINT="sshFingerprint";
	
	public static final String PREF_DEFAULT_PLAY_OPTION="defaultPlayOption";

	public static final String PREF_SHOW_A1_TV = "showA1Tv";
	public static final String PREF_AUDIO_OUTPUT = "audioOutput";
	public static final String PREF_LAST_TAB = "lastTab";
	public static final String PREF_LIGHT_THEME = "lightTheme";
	//public static final String PREF_YOUTUBE_HD = "youtubeHd";
	//public static final String PREF_USE_LIVE= "UseLive";
	public static final String PREF_SLIDESHOW_DELAY= "slideshowDelay";
	public static final String PREF_AUTOSTART_QUEUE= "autostartQueue";
	public static final String PREF_YOUTUBE_QUALITY= "youtubeQuality";
	public static final String PREF_MAX_PLAYLIST_SIZE="maxPlaylistSize";
	public static final String PREF_AUDIO_OFFSET= "audioOffset";
	public static final String PREF_YOUTUBE_HTTP= "youtubeHTTP";
	public static final String PREF_LIVE_OPTIONS= "liveOptions";
	public static final String PREF_OPEN_ON_QUEUE="openOnQueue";
	public static final String PREF_SHOW_QUEUE_SHARE="showQueueShare";
	public static final String PREF_HIDE_MEDIA_EXTIONSIONS="hideExtensions";
	public static final String PREF_DIRS_HOME_SCREEN="dirsHomeScreen";
	
	public static final String PREF_OPEN_QUEUE_SWIPE="openQueueWithSwipe";
	public static final String PREF_MINI_QUEUE_BUTTON="miniQueueButton";
	
	public static final String PREF_OMXPLAYER_OPTIONS = "omxplayerOptions";
	public static final String PREF_OMXIV_OPTIONS = "omxivOptions";
	public static final String PREF_ALSA_DEVICE= "alsaDevice";
	public static final String PREF_TEMP_DIR = "tempDir";
	public static final String PREF_CUSTOM_CMD = "customCMD";
	public static final String PREF_CUSTOM_CMD2 = "customCMD2";

	public static final String PREF_STREAM_PORT = "streamPort";
	
	//public static final String PREF_EXPERIMENTAL= "experimental";
	
	//public static final String PREF_SUBTITLES ="subtitles";
	public static final String PREF_SUBTITLES_LEFT ="subtitlesLeft";
	public static final String PREF_SUBTITLE_BOXES= "subtitleBoxes";
	public static final String PREF_SUBTITLE_SIZE= "subtitleSize";
	
	public static final String STREAMING_FILE = "streaming_File";
	public static final String PLAYLIST_DIR = "playlistDir";
	public static final String LIST_POSTION= "position";
	public static final String A1_TV_STREAM_LIST_NAME = "A1 TV";
	
	public static final String LAST_UPDATE_QUEUE_FILE ="last_update_queue";
	
	public static final int MAX_FILE_SIZE=100000;
	public static final int SUBTITLE_OFF_ID=9999;
	public static final String UNKNOWN_DATA = "Unknown";
	
	public static final String[] SUPPORTED_STREAMS= { "http://", "https://", "rtmp://", 
		"udp://", "rtsp://", "rtp://", "mms://", "mmsh://", "mmst://", "mmsu://", "ftp://", "sftp://" };
	
	public static final String[] COMMON_NON_PLAYLIST_FILE_EXTENSIONS = {
		".pdf" , ".doc", ".docx", ".zip", ".rar", ".xls", ".xlsx", ".xml", ".ppt", ".pptx", ".apk", ".log", ".tar",
		".vcf"
	};
	
	public static final String[] COMMON_MULTIMEDIA_FILE_EXTENSIONS = {
		".3gp",".3gpp", ".3g2", ".avi", ".bdvm", ".flv", ".m2ts", ".m4v", ".mkv", ".mov", 
		".mp4", ".mpeg", ".mpg", ".mts", ".ogm", ".ogv", ".qt", ".rm", ".sbe", ".ts",
		".wmv", ".wtv", ".ps", ".vob", ".mp3", ".mpa", ".ra", ".wav",".wma", ".ogg", ".aac", ".m4a", ".m4b", 
		".flac", ".aif", ".aiff", ".aifc", ".3ga", ".ape", ".aa3", ".oma", ".at3", ".au", ".snd", ".mpc", ".mp+",
		".mpp", ".shn", ".tta", ".mka", ".ac3", ".dts", ".oga", ".svi", ".m2v", ".mp2", ".mpe", ".mpv", ".asf",
		".webm", ".la", ".mp2", ".ast", ".m4p", ".swa", ".spx", ".pac", ".amr" };
	
	public static final String[] COMMON_AUDIO_FILE_EXTENSIONS = {".mp3", ".mpa", ".ra", ".wav",".wma", ".ogg", ".aac", ".m4a", ".m4b", 
		".flac", ".aif", ".aiff", ".aifc", ".3ga", ".ape", ".aa3", ".oma", ".at3", ".au", ".snd", ".mpc", ".mp+",
		".mpp", ".shn", ".tta", ".mka", ".ac3", ".dts", ".oga", ".la", ".mp2", ".ast", ".swa", ".spx", ".pac", ".amr" };
	
	public static final String[] COMMON_IMAGE_FILE_EXTENSIONS = { ".jpg", ".jpeg", ".png", ".bmp", ".gif", 
		".tiff", ".tif",};
	
	public static final String[] LANGUAGE_ABBREVIATION = { "eng", "en" , "deu", "de", "ger", "fr", "fra",
		"fre", "hi", "hin", "es", "spa", "pt", "por", "pl", "pol", "it", "ita", "hu", "hun", "fi", "fin",
		"da", "dan", "cs", "ces", "cze", "af", "afr", "ca", "cat", "zh", "zho", "chi", "hr", "hrv", "nl",
		"nld", "dut", "el", "ell", "gre", "ga", "gle", "ja", "jpn", "ru", "rus", "sr", "srp", "sk", "slk", 
		"slo", "sl", "slv", "sv", "swe", "tr", "tur", "uk", "ukr", "bg", "bul", "is", "isl", "ice", "ar",
		"ara", "no", "nor", "und" };
		
	public static final String[] LANGUAGE_FULL = { "English", "English" , "Deutsch", "Deutsch", "Deutsch",
		"Français", "Français", "Français", "हिन्दी", "हिन्दी", "Español", "Español", "Português", "Português",
		"Polski", "Polski", "Italiano", "Italiano", "Magyar", "Magyar", "Suomi", "Suomi", "Dansk", "Dansk",
		"Čeština", "Čeština", "Čeština", "Afrikaans", "Afrikaans", "Català", "Català", "中文", "中文", "中文",
		"Hrvatski", "Hrvatski", "Nederlands", "Nederlands", "Nederlands", "ελληνικά", "ελληνικά", "ελληνικά",
		"Gaeilge", "Gaeilge", "日本語", "日本語", "Русский", "Русский", "српски језик", "српски језик",
		"Slovenčina", "Slovenčina", "Slovenčina", "Slovenski", "Slovenski", "Svenska", "Svenska", "Türkçe",
		"Türkçe","українська мова", "українська мова", "български език", "български език", "Íslenska",
		"Íslenska", "Íslenska", "العربية", "العربية", "Norsk", "Norsk", "Undefined" };

}
