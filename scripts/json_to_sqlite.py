import json
import sqlite3
import os
import requests

JSON_INPUT_FILE = "quran_data.json"
SQLITE_OUTPUT_FILE = "quran.db"
API_BASE_URL = "https://api.alquran.cloud/v1/"


def fetch_expected_counts():
    """Fetch expected ayah counts from the API for validation."""
    try:
        response = requests.get(f"{API_BASE_URL}surah", timeout=30)
        response.raise_for_status()
        data = response.json()
        counts = [s['numberOfAyahs'] for s in data['data']]
        return counts, sum(counts)
    except:
        # Return hardcoded fallback if API fails
        return [
            7, 286, 200, 176, 120, 165, 206, 75, 129, 109, 123, 111, 43, 52, 99, 128, 111, 110,
            98, 135, 112, 78, 118, 64, 77, 227, 93, 88, 69, 60, 34, 30, 73, 54, 45, 83, 182, 88,
            75, 85, 54, 53, 89, 59, 37, 35, 38, 29, 18, 45, 60, 49, 62, 55, 78, 96, 29, 22, 24, 13,
            14, 11, 11, 18, 12, 12, 30, 52, 52, 44, 28, 28, 20, 56, 40, 31, 50, 40, 46, 42, 29, 19, 36,
            25, 22, 17, 19, 26, 30, 20, 15, 21, 11, 8, 8, 19, 5, 8, 8, 11, 11, 8, 3, 9, 5, 4, 7, 3, 6, 3,
            5, 4, 5, 6
        ], 6236


def validate_json_data(data):
    """Validate that JSON data has all required content."""
    errors = []
    warnings = []

    surahs = data.get("surahs", [])
    verses = data.get("verses", [])

    # Get expected counts from API
    expected_counts, expected_total = fetch_expected_counts()

    # Check surah count
    if len(surahs) != 114:
        errors.append(f"Expected 114 surahs, found {len(surahs)}")

    # Check total ayah count
    if len(verses) != expected_total:
        errors.append(f"Expected {expected_total} ayahs, found {len(verses)}")

    # Check ayah counts per surah
    actual_ayah_counts = {}
    for verse in verses:
        surah_num = verse.get("surahNumber")
        if surah_num:
            actual_ayah_counts[surah_num] = max(actual_ayah_counts.get(surah_num, 0), verse.get("ayahInSurah", 0))

    for surah_num, expected_count in enumerate(expected_counts, start=1):
        actual_count = actual_ayah_counts.get(surah_num, 0)
        if actual_count != expected_count:
            errors.append(f"Surah {surah_num}: expected {expected_count} ayahs, found {actual_count}")

    return errors, warnings


def create_and_populate_db(json_file_path, db_file_path):
    # Ensure the JSON input file exists
    if not os.path.exists(json_file_path):
        print(f"Error: JSON input file not found at {json_file_path}")
        return

    # Load data from the JSON file
    with open(json_file_path, 'r', encoding='utf-8') as f:
        data = json.load(f)

    # Validate the data
    errors, warnings = validate_json_data(data)

    if warnings:
        print("Warnings:")
        for warning in warnings:
            print(f"  - {warning}")

    if errors:
        print("Validation errors detected. Cannot proceed:")
        for error in errors:
            print(f"  - {error}")
        print("\nAborting database creation.")
        return

    print("Data validation passed.")

    surahs_data = data.get("surahs", [])
    verses_data = data.get("verses", [])

    # Connect to SQLite database (creates it if it doesn't exist)
    conn = sqlite3.connect(db_file_path)
    cursor = conn.cursor()

    print(f"Creating database {db_file_path}...")

    # Drop tables if they exist (for clean regeneration)
    cursor.execute("DROP TABLE IF EXISTS verses")
    cursor.execute("DROP TABLE IF EXISTS surahs")

    # Create surahs table
    cursor.execute('''
        CREATE TABLE surahs (
            surahNumber INTEGER NOT NULL PRIMARY KEY,
            surahName TEXT NOT NULL,
            surahNameEn TEXT NOT NULL,
            revelationType TEXT NOT NULL,
            numberOfAyahs INTEGER NOT NULL
        )
    ''')

    # Create verses table
    # Note: SQLite stores booleans as integers (0 for false, 1 for true)
    cursor.execute('''
        CREATE TABLE verses (
            id INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
            surahNumber INTEGER NOT NULL,
            ayahInSurah INTEGER NOT NULL,
            text TEXT NOT NULL,
            hizbQuarter INTEGER NOT NULL,
            rukuNumber INTEGER NOT NULL,
            page INTEGER NOT NULL,
            manzil INTEGER NOT NULL,
            sajda INTEGER NOT NULL,
            globalAyahNumber INTEGER NOT NULL,
            surahName TEXT NOT NULL,
            surahNameEn TEXT NOT NULL,
            revelationType TEXT NOT NULL,
            numberOfAyahs INTEGER NOT NULL,
            FOREIGN KEY(surahNumber) REFERENCES surahs(surahNumber) ON DELETE CASCADE
        )
    ''')

    # Create index for efficient lookup by surahNumber on verses table
    cursor.execute("CREATE INDEX index_verses_on_surahNumber ON verses (surahNumber)")

    print("Tables created.")

    # Insert surahs data
    surah_insert_query = '''
        INSERT INTO surahs (surahNumber, surahName, surahNameEn, revelationType, numberOfAyahs)
        VALUES (?, ?, ?, ?, ?)
    '''
    surahs_to_insert = [
        (s['surahNumber'], s['surahName'], s['surahNameEn'], s['revelationType'], s['numberOfAyahs'])
        for s in surahs_data
    ]
    cursor.executemany(surah_insert_query, surahs_to_insert)
    print(f"Inserted {len(surahs_to_insert)} surahs.")

    # Insert verses data
    verse_insert_query = '''
        INSERT INTO verses (surahNumber, ayahInSurah, text, hizbQuarter, rukuNumber, page, manzil, sajda, globalAyahNumber, surahName, surahNameEn, revelationType, numberOfAyahs)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
    '''
    # Convert Python boolean to SQLite integer (0 or 1)
    verses_to_insert = [
        (v['surahNumber'], v['ayahInSurah'], v['text'], v['hizbQuarter'], v['rukuNumber'], v['page'], v['manzil'], int(v['sajda']), v['globalAyahNumber'], v['surahName'], v['surahNameEn'], v['revelationType'], v['numberOfAyahs'])
        for v in verses_data
    ]
    cursor.executemany(verse_insert_query, verses_to_insert)
    print(f"Inserted {len(verses_to_insert)} verses.")

    # Commit changes and close the connection
    conn.commit()
    conn.close()
    print(f"Database {db_file_path} created successfully with data.")

if __name__ == "__main__":
    create_and_populate_db(JSON_INPUT_FILE, SQLITE_OUTPUT_FILE)

