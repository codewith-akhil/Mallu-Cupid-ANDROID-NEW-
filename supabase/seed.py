#!/usr/bin/env python3
"""
Mallu Cupid — Supabase seed script.

Creates 8 sample auth users and inserts their dating-profile data into
public.profiles + public.profile_photos + public.profile_prompts so the
swipe deck is populated for the first real user.

Usage:
  python3 supabase/seed.py

Requires: psycopg2-binary (already installed in this env).
Reads nothing from the environment — credentials are hard-coded for this
one-off seeding operation (they match the values in the project's env).
"""
import psycopg2, requests, json, time, sys

SUPABASE_URL = "https://zetvumxhtkomajmdxfbx.supabase.co"
SERVICE_ROLE = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InpldHZ1bXhodGtvbWFqbWR4ZmJ4Iiwicm9sZSI6InNlcnZpY2Vfcm9sZSIsImlhdCI6MTc4OTIxODkxNCwiZXhwIjoyMTA0Nzk0OTE0fQ.rmKwlOX2gTKne-elMsULwJtGc6xX13HFVHLH9LbduzA"
DB_PASSWORD = 'Gptimagen5"5656'
PROJECT = 'zetvumxhtkomajmdxfbx'
POOLER_HOST = 'aws-0-ap-northeast-1.pooler.supabase.com'

# 8 sample profiles (matches SampleProfiles.list in the Android app).
SAMPLE_PROFILES = [
    {
        "name": "Remy", "birth_year": 1987, "gender": "Woman", "city": "Sydney",
        "bio": "I'm a simple person with a slightly complicated brain. I overthink everything, laugh at the most random things, and ask way too many questions. I love good conversations, good food, spontaneous plans, and people who can keep up with my thoughts.",
        "profession": "Product Consultant", "looking_for": "Long-term partner",
        "zodiac": "Scorpio", "anthem": "Watermelon Sugar · Harry Styles",
        "communication_style": "Better in person", "love_style": "Quality time",
        "education": "Master's degree", "drinking": "Socially", "smoking": "Non-smoker",
        "workout": "Sometimes", "pets": "Dog lover",
        "interests": ["Travel", "Coffee", "Music", "Conversations", "Foodie", "Books"],
        "photos": [
            "https://images.unsplash.com/photo-1573496359142-b8d87734a5a2?auto=format&fit=crop&w=900&q=85",
            "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=900&q=85",
            "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=900&q=85",
            "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=900&q=85",
            "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=900&q=85",
        ],
        "prompts": [
            {"question": "A surprising thing about me is:", "answer": "I overthink everything and ask way too many questions 😄"},
            {"question": "My simple pleasures in life:", "answer": "Coffee, slow rains, and spontaneous late drives."},
        ],
        "is_verified": True, "is_online": True,
    },
    {
        "name": "Misba", "birth_year": 2004, "gender": "Woman", "city": "Sydney",
        "bio": "Art student who loves experimenting with watercolors, indie acoustic tracks, and late night tea discussions.",
        "profession": "Fine Arts & Design", "looking_for": "Long-term partner",
        "zodiac": "Libra", "anthem": "Golden Hour · JVKE",
        "communication_style": "Better in person", "love_style": "Presents",
        "education": "Bachelor degree", "drinking": "Not for me", "smoking": "Non-smoker",
        "workout": "Sometimes", "pets": "Don't have, but love",
        "interests": ["Art", "Painting", "Drawing", "Foodie", "Travel", "Museums"],
        "photos": [
            "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=900&q=85",
            "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=900&q=85",
            "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=900&q=85",
        ],
        "prompts": [
            {"question": "A surprising thing about me is:", "answer": "Talk a lot once I'm comfortable 😄"},
            {"question": "My simple pleasures in life:", "answer": "Sunset at the beach and freshly brewed tea."},
        ],
        "is_verified": False, "is_online": False,
    },
    {
        "name": "Resh", "birth_year": 1994, "gender": "Woman", "city": "Melbourne",
        "bio": "Looking for fun moments, spontaneous weekend trips, and someone who appreciates good banter.",
        "profession": "Marketing Strategist", "looking_for": "Short-term fun",
        "zodiac": "Leo", "anthem": "Photograph · Ed Sheeran",
        "communication_style": "Replies quickly", "love_style": "Quality time",
        "education": "Master's degree", "drinking": "Socially", "smoking": "Non-smoker",
        "workout": "Regularly", "pets": "Dog lover",
        "interests": ["Travel", "Cocktails", "Music", "Road trips", "Photography"],
        "photos": [
            "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=900&q=85",
            "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=900&q=85",
        ],
        "prompts": [
            {"question": "My ideal first date:", "answer": "Rooftop lounge, tapas, and good live music."},
        ],
        "is_verified": True, "is_online": True,
    },
    {
        "name": "HINASH", "birth_year": 2003, "gender": "Woman", "city": "London",
        "bio": "Fashion graduate & modest stylist. Tea over coffee, always.",
        "profession": "Fashion Stylist", "looking_for": "Long-term partner",
        "zodiac": "Taurus", "anthem": "Perfect · Ed Sheeran",
        "communication_style": "Better in person", "love_style": "Acts of service",
        "education": "Degree in Fashion", "drinking": "Not for me", "smoking": "Non-smoker",
        "workout": "Active", "pets": "Cat lover",
        "interests": ["Design", "Fashion", "Coffee", "Travel"],
        "photos": [
            "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=900&q=85",
            "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=900&q=85",
        ],
        "prompts": [
            {"question": "The quickest way to my heart is:", "answer": "Remembering the little details."},
        ],
        "is_verified": True, "is_online": False,
    },
    {
        "name": "Zainab", "birth_year": 1996, "gender": "Woman", "city": "Vancouver",
        "bio": "Curator & architect. Love minimalist aesthetics and cozy bookstores.",
        "profession": "Architectural Designer", "looking_for": "Serious commitment",
        "zodiac": "Virgo", "anthem": "Watermelon Sugar · Harry Styles",
        "communication_style": "Thoughtful texter", "love_style": "Deep talks",
        "education": "Bachelor of Architecture", "drinking": "Not for me", "smoking": "Non-smoker",
        "workout": "Pilates", "pets": "Pet-free",
        "interests": ["Architecture", "Coffee", "Books", "Art"],
        "photos": [
            "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=900&q=85",
            "https://images.unsplash.com/photo-1534528741775-53994a69daeb?auto=format&fit=crop&w=900&q=85",
        ],
        "prompts": [
            {"question": "A non-negotiable for me is:", "answer": "Kindness and emotional maturity."},
        ],
        "is_verified": True, "is_online": True,
    },
    {
        "name": "Aparna", "birth_year": 1996, "gender": "Woman", "city": "Toronto",
        "bio": "Doctor by training, classical dancer by heart. Finding calm in life's rhythm.",
        "profession": "Physician", "looking_for": "Long-term partner",
        "zodiac": "Cancer", "anthem": "Photograph · Ed Sheeran",
        "communication_style": "Calls over texts", "love_style": "Affection",
        "education": "MBBS, MD", "drinking": "Not for me", "smoking": "Non-smoker",
        "workout": "Yoga daily", "pets": "Has a Golden Retriever",
        "interests": ["Classical Dance", "Yoga", "Medicine", "Nature"],
        "photos": [
            "https://images.unsplash.com/photo-1517841905240-472988babdf9?auto=format&fit=crop&w=900&q=85",
        ],
        "prompts": [
            {"question": "I'm looking for someone who:", "answer": "Can hold a deep conversation and make me laugh."},
        ],
        "is_verified": True, "is_online": False,
    },
    {
        "name": "Unnati", "birth_year": 2004, "gender": "Woman", "city": "Sydney",
        "bio": "Psychology student & podcaster. Forever chasing sunsets and good conversations.",
        "profession": "Student / Creator", "looking_for": "Coffee date",
        "zodiac": "Gemini", "anthem": "Levitating · Dua Lipa",
        "communication_style": "Voice notes", "love_style": "Words of affirmation",
        "education": "Undergraduate", "drinking": "Socially", "smoking": "Non-smoker",
        "workout": "Gym 3x/week", "pets": "Love pets",
        "interests": ["Podcasts", "Psychology", "Beach days", "Baking"],
        "photos": [
            "https://images.unsplash.com/photo-1524504388940-b1c1722653e1?auto=format&fit=crop&w=900&q=85",
        ],
        "prompts": [
            {"question": "A surprising fact:", "answer": "I've recorded over 50 podcast episodes!"},
        ],
        "is_verified": True, "is_online": True,
    },
    {
        "name": "Zappy", "birth_year": 2001, "gender": "Man", "city": "Sydney",
        "bio": "Content creator & traveler. Always with a camera in hand.",
        "profession": "Photographer", "looking_for": "Free tonight",
        "zodiac": "Sagittarius", "anthem": "Blinding Lights · The Weeknd",
        "communication_style": "Casual & fun", "love_style": "Adventures",
        "education": "Media Studies", "drinking": "Socially", "smoking": "Non-smoker",
        "workout": "Skateboarding", "pets": "Dog lover",
        "interests": ["Photography", "Street Food", "Skate", "Film"],
        "photos": [
            "https://images.unsplash.com/photo-1507003211169-0a1dd7228f2d?auto=format&fit=crop&w=900&q=85",
        ],
        "prompts": [],
        "is_verified": True, "is_online": True,
    },
]


def create_auth_user(name: str, idx: int) -> str:
    """Create an auth.user via the admin API; return its UUID."""
    email = f"{name.lower().replace(' ', '.')}+seed{idx}@mallucupid.app"
    res = requests.post(
        f"{SUPABASE_URL}/auth/v1/admin/users",
        headers={
            "Authorization": f"Bearer {SERVICE_ROLE}",
            "apikey": SERVICE_ROLE,
            "Content-Type": "application/json",
        },
        json={
            "email": email,
            "password": f"SeedPass{idx}!Mallu",
            "email_confirm": True,
            "user_metadata": {"name": name, "is_seed": True},
        },
    )
    if res.status_code == 422:
        # User already exists — fetch its id.
        list_res = requests.get(
            f"{SUPABASE_URL}/auth/v1/admin/users",
            headers={"Authorization": f"Bearer {SERVICE_ROLE}", "apikey": SERVICE_ROLE},
        )
        users = list_res.json().get("users", [])
        for u in users:
            if u.get("email", "").lower() == email:
                return u["id"]
        raise RuntimeError(f"user exists but couldn't be found: {email}")
    if res.status_code not in (200, 201):
        raise RuntimeError(f"createUser {name}: HTTP {res.status_code} {res.text[:200]}")
    return res.json()["id"]


def insert_profile(conn, user_id: str, p: dict):
    cur = conn.cursor()
    # Upsert the profile row.
    cur.execute("""
        insert into public.profiles (
            id, name, gender, birth_year, city, bio, profession, looking_for,
            zodiac, anthem, communication_style, love_style, education,
            drinking, smoking, workout, pets, interests, is_verified, is_online,
            registered_email, created_at, updated_at
        ) values (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s, now(), now())
        on conflict (id) do update set
            name=excluded.name, bio=excluded.bio, is_verified=excluded.is_verified
    """, (
        user_id, p["name"], p["gender"], p["birth_year"], p["city"], p["bio"],
        p["profession"], p["looking_for"], p["zodiac"], p["anthem"],
        p["communication_style"], p["love_style"], p["education"],
        p["drinking"], p["smoking"], p["workout"], p["pets"],
        p["interests"], p["is_verified"], p["is_online"],
        f"{p['name'].lower().replace(' ', '.')}+seed@mallucupid.app",
    ))
    # Replace photos.
    cur.execute("delete from public.profile_photos where user_id = %s", (user_id,))
    for i, url in enumerate(p["photos"]):
        cur.execute("""
            insert into public.profile_photos (user_id, url, position, is_primary)
            values (%s, %s, %s, %s)
        """, (user_id, url, i, i == 0))
    # Replace prompts.
    cur.execute("delete from public.profile_prompts where user_id = %s", (user_id,))
    for i, pr in enumerate(p["prompts"]):
        cur.execute("""
            insert into public.profile_prompts (user_id, position, question, answer)
            values (%s, %s, %s, %s)
        """, (user_id, i, pr["question"], pr["answer"]))
    # Upsert settings.
    cur.execute("""
        insert into public.user_settings (user_id) values (%s)
        on conflict (user_id) do nothing
    """, (user_id,))
    conn.commit()


def main():
    print(f"Connecting to Supabase pooler ({POOLER_HOST})…")
    conn = psycopg2.connect(
        host=POOLER_HOST, port=6543, dbname="postgres",
        user=f"postgres.{PROJECT}", password=DB_PASSWORD, connect_timeout=15,
    )
    conn.autocommit = False
    print("✓ Connected.\n")

    for idx, p in enumerate(SAMPLE_PROFILES):
        print(f"[{idx+1}/{len(SAMPLE_PROFILES)}] {p['name']}…")
        user_id = create_auth_user(p["name"], idx)
        print(f"  auth user: {user_id}")
        insert_profile(conn, user_id, p)
        print(f"  ✓ profile + {len(p['photos'])} photos + {len(p['prompts'])} prompts")
        time.sleep(0.2)

    print("\n✅ Seed complete. 8 sample profiles are now swipable.")
    # Quick verify
    cur = conn.cursor()
    cur.execute("select count(*) from public.profiles")
    print(f"   profiles in DB: {cur.fetchone()[0]}")
    conn.close()


if __name__ == "__main__":
    sys.exit(main() or 0)
