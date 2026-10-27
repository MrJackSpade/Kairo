import unittest

from launchbox_artwork import References

REFERENCES = References("KairoMSX", (80,))
IMAGE = {"id": "fb1d63ca-8d4a-4bfe-825f-96740bc7698b", "format": 1, "revision": 0}
HOSTED = "art/catalog/moby/30376/box-9bf46453978e.webp"


class ExpandTest(unittest.TestCase):
    def test_a_mixed_group_keeps_its_self_hosted_image(self):
        group = {"platform": 80, "game": 155187, "boxArt": HOSTED,
                 "boxArtUrl": REFERENCES.self_hosted_base + HOSTED, "preview": IMAGE}
        art = REFERENCES.expand(group)
        self.assertEqual(art["boxArt"], HOSTED)
        self.assertEqual(art["boxArtUrl"], REFERENCES.self_hosted_base + HOSTED)
        self.assertEqual(art["previewUrl"], "https://images.launchbox-app.com/" + IMAGE["id"] + ".png")
        self.assertEqual(REFERENCES.compact(art), group)

    def test_an_image_url_without_its_image_is_rejected(self):
        launchbox = {"platform": 80, "game": 155187, "boxArt": IMAGE}
        mixed = {"platform": 80, "game": 155187, "boxArt": HOSTED,
                 "boxArtUrl": REFERENCES.self_hosted_base + HOSTED}
        for bad in ({**launchbox, "previewUrl": "https://example.com/"},
                    {"platform": 80, "game": 155187, "preview": IMAGE,
                     "boxArtUrl": REFERENCES.self_hosted_base + HOSTED},
                    {**mixed, "previewUrl": "https://example.com/"}):
            with self.subTest(bad=bad), self.assertRaisesRegex(ValueError, "an image URL without its image"):
                REFERENCES.expand(bad)


if __name__ == "__main__":
    unittest.main()
