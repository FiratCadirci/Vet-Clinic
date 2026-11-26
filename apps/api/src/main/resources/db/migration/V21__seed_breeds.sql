-- Tür bazlı ırk kataloğu (referans verisi). Şema değişmez.
INSERT INTO vet.breeds (species_id, name, is_mixed)
SELECT s.id, b.name, b.is_mixed
FROM (VALUES
    ('DOG', 'Melez (Karışık)', true),
    ('DOG', 'Golden Retriever', false), ('DOG', 'Labrador Retriever', false),
    ('DOG', 'Alman Çoban Köpeği', false), ('DOG', 'Kangal', false),
    ('DOG', 'Akbaş', false), ('DOG', 'Husky', false), ('DOG', 'Pomeranian', false),
    ('DOG', 'Yorkshire Terrier', false), ('DOG', 'Cocker Spaniel', false),
    ('DOG', 'Rottweiler', false), ('DOG', 'Doberman', false),
    ('DOG', 'Pug', false), ('DOG', 'Fransız Bulldog', false),
    ('DOG', 'Chihuahua', false), ('DOG', 'Border Collie', false),
    ('DOG', 'Beagle', false), ('DOG', 'Poodle (Kaniş)', false),
    ('DOG', 'Maltese Terrier', false), ('DOG', 'Shih Tzu', false),
    ('CAT', 'Melez (Karışık)', true),
    ('CAT', 'Tekir (Domestic Shorthair)', false), ('CAT', 'British Shorthair', false),
    ('CAT', 'Scottish Fold', false), ('CAT', 'Van Kedisi', false),
    ('CAT', 'Ankara Kedisi', false), ('CAT', 'Persian (İran Kedisi)', false),
    ('CAT', 'Siyam (Siamese)', false), ('CAT', 'Maine Coon', false),
    ('CAT', 'Bengal', false), ('CAT', 'Sphynx', false), ('CAT', 'Ragdoll', false),
    ('RABBIT', 'Melez (Karışık)', true),
    ('RABBIT', 'Holland Lop', false), ('RABBIT', 'Mini Rex', false),
    ('RABBIT', 'Netherland Dwarf', false), ('RABBIT', 'Angora', false),
    ('RABBIT', 'Lionhead', false),
    ('BIRD', 'Melez (Karışık)', true),
    ('BIRD', 'Muhabbet Kuşu', false), ('BIRD', 'Kanarya', false),
    ('BIRD', 'Sevda Papağanı', false), ('BIRD', 'Cennet Papağanı', false),
    ('BIRD', 'Kakadu', false), ('BIRD', 'Jako (Gri Papağan)', false),
    ('BIRD', 'Ispinoz', false),
    ('RODENT', 'Melez (Karışık)', true),
    ('RODENT', 'Hamster (Suriye)', false), ('RODENT', 'Hamster (Cüce)', false),
    ('RODENT', 'Kobay (Guinea Pig)', false), ('RODENT', 'Sıçan', false),
    ('RODENT', 'Fare', false), ('RODENT', 'Gerbil', false), ('RODENT', 'Çinçilla', false),
    ('REPTILE', 'Su Kaplumbağası (Kırmızı Yanaklı)', false),
    ('REPTILE', 'Kara Kaplumbağası', false), ('REPTILE', 'Sakallı Ejderha', false),
    ('REPTILE', 'Leopar Gekosu', false), ('REPTILE', 'İguana', false),
    ('REPTILE', 'Kral Piton', false), ('REPTILE', 'Mısır Yılanı', false),
    ('OTHER', 'Belirtilmemiş', false)
) AS b(species_code, name, is_mixed)
JOIN vet.species s ON s.code = b.species_code
ON CONFLICT (species_id, name) DO NOTHING;
