/*
 * Agenticai Extension for Krista
 * Copyright (C) 2025 Krista Software
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package app.krista.extensions.hook.common;

public enum LanguageCode {
    EN("en", "English"),
    ES("es", "Spanish"),
    FR("fr", "French"),
    DE("de", "German"),
    IT("it", "Italian"),
    PT("pt", "Portuguese"),
    RU("ru", "Russian"),
    JA("ja", "Japanese"),
    KO("ko", "Korean"),
    ZH("zh", "Chinese"),
    AR("ar", "Arabic"),
    HI("hi", "Hindi"),
    NL("nl", "Dutch"),
    SV("sv", "Swedish"),
    NO("no", "Norwegian"),
    DA("da", "Danish"),
    FI("fi", "Finnish"),
    PL("pl", "Polish"),
    TR("tr", "Turkish"),
    TH("th", "Thai"),
    VI("vi", "Vietnamese"),
    ID("id", "Indonesian"),
    MS("ms", "Malay"),
    HE("he", "Hebrew"),
    CS("cs", "Czech"),
    HU("hu", "Hungarian"),
    RO("ro", "Romanian"),
    BG("bg", "Bulgarian"),
    HR("hr", "Croatian"),
    SK("sk", "Slovak"),
    SL("sl", "Slovenian"),
    ET("et", "Estonian"),
    LV("lv", "Latvian"),
    LT("lt", "Lithuanian"),
    UK("uk", "Ukrainian"),
    EL("el", "Greek"),
    MT("mt", "Maltese"),
    GA("ga", "Irish");

    private final String code;
    private final String displayName;

    LanguageCode(String code, String displayName) {
        this.code = code;
        this.displayName = displayName;
    }

    public String getCode() {
        return code;
    }

    public static String getDisplayName(String code) {
        if (code == null) return "English"; // Default to English instead of "Unknown"
        
        for (LanguageCode lang : values()) {
            if (lang.code.equalsIgnoreCase(code)) {
                return lang.displayName;
            }
        }
        return "English"; // Default to English if code not found
    }

}
