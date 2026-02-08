import requests
import json
import time
import argparse
from requests.exceptions import RequestException

BASE_URL = "https://api.alquran.cloud/v1/"
OUTPUT_FILENAME = "quran_data.json"
RATE_LIMIT_DELAY_SECONDS = 0.2  # Adjust as needed, e.g., 0.1 for 100ms
MAX_RETRIES = 3  # Number of retry attempts for failed requests
RETRY_DELAY_SECONDS = 2.0  # Delay between retries


def fetch_with_retry(url, max_retries=MAX_RETRIES):
    """Fetch URL with retry logic for transient errors."""
    for attempt in range(max_retries):
        try:
            response = requests.get(url, timeout=30)
            response.raise_for_status()
            return response
        except RequestException as e:
            if attempt < max_retries - 1:
                print(f"  Request failed (attempt {attempt + 1}/{max_retries}), retrying in {RETRY_DELAY_SECONDS}s: {e}")
                time.sleep(RETRY_DELAY_SECONDS)
            else:
                raise e
    return None


def fetch_all_surahs():
    print("Fetching all surahs...")
    response = fetch_with_retry(f"{BASE_URL}surah")
    data = response.json()
    surahs_data = []
    for surah_dto in data['data']:
        surahs_data.append({
            "surahNumber": surah_dto['number'],
            "surahName": surah_dto['name'],
            "surahNameEn": surah_dto['englishName'],
            "revelationType": surah_dto['revelationType'],
            "numberOfAyahs": surah_dto['numberOfAyahs']
        })
    print(f"Fetched {len(surahs_data)} surahs.")
    return surahs_data


def fetch_all_verses_by_page():
    all_verses_data = []
    pages_skipped = 0

    # Get total pages from a sample request to be more accurate
    total_pages = 604  # Fallback - Uthmani Mushaf has 604 pages

    for page_number in range(1, total_pages + 1):
        print(f"Fetching page {page_number}/{total_pages}...")
        try:
            response = fetch_with_retry(f"{BASE_URL}page/{page_number}")
            page_data = response.json()['data']

            for ayah_dto in page_data['ayahs']:
                surah_info = ayah_dto['surah']
                all_verses_data.append({
                    "surahNumber": surah_info['number'],
                    "ayahInSurah": ayah_dto['numberInSurah'],
                    "text": ayah_dto['text'],
                    "hizbQuarter": ayah_dto['hizbQuarter'],
                    "rukuNumber": ayah_dto['ruku'],
                    "page": ayah_dto['page'],
                    "manzil": ayah_dto['manzil'],
                    "sajda": ayah_dto['sajda'] if isinstance(ayah_dto['sajda'], bool) else False,
                    "globalAyahNumber": ayah_dto['number'],
                    "surahName": surah_info['name'],
                    "surahNameEn": surah_info['englishName'],
                    "revelationType": surah_info['revelationType'],
                    "numberOfAyahs": surah_info['numberOfAyahs']
                })
            time.sleep(RATE_LIMIT_DELAY_SECONDS)
        except RequestException as e:
            print(f"Error fetching page {page_number}: {e}")
            pages_skipped += 1
            continue
    print(f"Fetched {len(all_verses_data)} verses.")
    if pages_skipped > 0:
        print(f"Skipped {pages_skipped} pages due to errors.")
    return all_verses_data


def fetch_all_verses_by_juz(juz_number):
    """Fetch all verses for a specific juz using the juz endpoint."""
    all_verses_data = []
    print(f"Fetching verses for Juz {juz_number}/30...")

    try:
        response = fetch_with_retry(f"{BASE_URL}juz/{juz_number}")
        juz_data = response.json()['data']

        for ayah_dto in juz_data['ayahs']:
            surah_info = ayah_dto['surah']
            all_verses_data.append({
                "surahNumber": surah_info['number'],
                "ayahInSurah": ayah_dto['numberInSurah'],
                "text": ayah_dto['text'],
                "hizbQuarter": ayah_dto['hizbQuarter'],
                "rukuNumber": ayah_dto['ruku'],
                "page": ayah_dto['page'],
                "manzil": ayah_dto['manzil'],
                "sajda": ayah_dto['sajda'] if isinstance(ayah_dto['sajda'], bool) else False,
                "globalAyahNumber": ayah_dto['number'],
                "surahName": surah_info['name'],
                "surahNameEn": surah_info['englishName'],
                "revelationType": surah_info['revelationType'],
                "numberOfAyahs": surah_info['numberOfAyahs']
            })
        time.sleep(RATE_LIMIT_DELAY_SECONDS)
    except RequestException as e:
        print(f"Error fetching Juz {juz_number}: {e}")

    print(f"Fetched {len(all_verses_data)} verses from Juz {juz_number}.")
    return all_verses_data


def fetch_all_verses_by_juzes():
    """Fetch all verses by iterating through all 30 juz."""
    all_verses_data = []
    total_juz = 30

    for juz_number in range(1, total_juz + 1):
        verses = fetch_all_verses_by_juz(juz_number)
        all_verses_data.extend(verses)

    return all_verses_data

def generate_quran_data_json(use_juz=False):
    quran_data = {
        "surahs": [],
        "verses": []
    }

    surahs = fetch_all_surahs()
    quran_data["surahs"] = surahs
    time.sleep(RATE_LIMIT_DELAY_SECONDS)  # Delay after surah fetch

    if use_juz:
        print("Fetching verses by juz...")
        verses = fetch_all_verses_by_juzes()
    else:
        print("Fetching verses by page...")
        verses = fetch_all_verses_by_page()

    quran_data["verses"] = verses

    with open(OUTPUT_FILENAME, 'w', encoding='utf-8') as f:
        json.dump(quran_data, f, ensure_ascii=False, indent=2)
    print(f"Successfully generated {OUTPUT_FILENAME}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Download Quran data from alquran.cloud API")
    parser.add_argument("--juz", action="store_true", help="Use juz endpoint (30 requests) instead of page endpoint (604 requests)")
    args = parser.parse_args()

    generate_quran_data_json(use_juz=args.juz)

