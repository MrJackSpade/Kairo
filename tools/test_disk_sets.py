import unittest

from disk_sets import disk_sets, ungrouped_sets

FD = "sha256-fd-v1:"
CD = "sha256-cd-v1:"
FLOPPY_B = {"fd": ("fd", "floppyB", False)}


def disk(archive, member, content):
    return {"archive": archive, "member": member, "contentId": content}


class DiskSetsTest(unittest.TestCase):
    def test_one_archive_collapses_behind_disk_a_and_inserts_disk_b(self):
        disks = [disk("Apple Club 1/Apple Club 1 [FD].7z", "Apple Club 1 (Disk B).d88", FD + "b"),
                 disk("Apple Club 1/Apple Club 1 [FD].7z", "Apple Club 1 (Disk A).d88", FD + "a")]
        self.assertEqual(disk_sets(disks, ["fd"], FLOPPY_B),
                         {FD + "a": {"setDisks": [FD + "b"], "media": [{"role": "floppyB", "contentId": FD + "b"}]}})

    def test_alternates_and_user_disks_are_hidden_but_never_start_or_go_in_drive_b(self):
        disks = [disk("Angelus/Angelus [FD].7z", "alts/Angelus (Disk A) [Alt 1].d88", FD + "alt"),
                 disk("Angelus/Angelus [FD].7z", "Angelus (Disk A).d88", FD + "a"),
                 disk("Angelus/Angelus [FD].7z", "Angelus (User Disk).d88", FD + "user")]
        self.assertEqual(disk_sets(disks, ["fd"], FLOPPY_B), {FD + "a": {"setDisks": [FD + "alt", FD + "user"]}})

    def test_a_data_disk_never_starts_a_set_and_hides_behind_the_folders_main_release(self):
        # KairoST#36/#37: Astaroth's second disk, read sector by sector, has nothing that starts.
        disks = [disk("ST/Astaroth (1989)(Hewson)[cr Delight].zip", None, FD + "crack"),
                 disk("ST/Astaroth (1989)(Hewson)(Disk 2 of 2)[Disk 2 in Games STX].zip", None, FD + "d2"),
                 disk("STX/Astaroth (1989)(Hewson)(Disk 1 of 2).zip", None, FD + "x1"),
                 disk("STX/Astaroth (1989)(Hewson)(Disk 2 of 2).zip", None, FD + "x2")]
        data = {FD + "d2", FD + "x2"}
        self.assertEqual(disk_sets(disks, ["fd"], {}, lambda content_id: content_id in data),
                         {FD + "crack": {"setDisks": [FD + "d2"]}, FD + "x1": {"setDisks": [FD + "x2"]}})

    def test_a_broken_dump_hides_behind_one_that_plays(self):
        # KairoST#38: 9 Lives halts on every TOS; its cracks play.
        disks = [disk("ST/9 Lives (1990)(Frames).zip", None, FD + "clean"),
                 disk("ST/9 Lives (1990)(Frames)[cr Empire].zip", None, FD + "empire"),
                 disk("ST/9 Lives (1990)(Frames)[cr Atari Legend][t].zip", None, FD + "legend")]
        broken = {FD + "clean"}
        sets = disk_sets(disks, ["fd"], {}, broken_disk=lambda content_id: content_id in broken)
        self.assertEqual(list(sets), [FD + "empire"])
        self.assertEqual(set(sets[FD + "empire"]["setDisks"]), {FD + "clean", FD + "legend"})

    def test_a_broken_disk_is_not_a_sets_entry_while_another_disk_works(self):
        disks = [disk("Game/Game [FD].7z", "Game (Disk A).d88", FD + "a"),
                 disk("Game/Game [FD].7z", "Game (Disk B).d88", FD + "b")]
        self.assertEqual(disk_sets(disks, ["fd"], {}, broken_disk=lambda content_id: content_id == FD + "a"),
                         {FD + "b": {"setDisks": [FD + "a"]}})
        self.assertEqual(disk_sets(disks, ["fd"], {}, broken_disk=lambda content_id: True),
                         {FD + "a": {"setDisks": [FD + "b"]}})

    def test_a_set_of_only_data_disks_has_no_entry(self):
        disks = [disk("Game/Game (Disk 1 of 2).zip", None, FD + "1"), disk("Game/Game (Disk 2 of 2).zip", None, FD + "2"),
                 disk("Game/Other Game.zip", None, FD + "other")]
        self.assertEqual(disk_sets(disks, ["fd"], {}, lambda content_id: content_id != FD + "other"),
                         {FD + "other": {"setDisks": [FD + "1", FD + "2"]}})

    def test_tosec_archives_of_one_set_group_by_the_name_before_the_disk_tag(self):
        disks = [disk("Fray (1990)(Micro Cabin)(JP)(Disk 3 of 5)(Opening Disk 2).zip", None, FD + "3"),
                 disk("Fray (1990)(Micro Cabin)(JP)(Disk 1 of 5)(Program).zip", None, FD + "1"),
                 disk("Fray (1990)(Micro Cabin)(JP)(Disk 2 of 5)(Opening Disk 1).zip", None, FD + "2")]
        self.assertEqual(disk_sets(disks, ["fd"], {}), {FD + "1": {"setDisks": [FD + "2", FD + "3"]}})

    def test_alternate_dump_sets_keep_their_entries_and_the_first_set_hides_them(self):
        disks = [disk("Game/Game [FD] [Set 1].7z", "Game [Set 1] (Disk A).d88", FD + "1a"),
                 disk("Game/Game [FD] [Set 1].7z", "Game [Set 1] (Disk B).d88", FD + "1b"),
                 disk("Game/Game [FD] [Set 2].7z", "Game [Set 2] (Disk A).d88", FD + "2a"),
                 disk("Game/Game [FD] [Set 2].7z", "Game [Set 2] (Disk B).d88", FD + "2b"),
                 disk("Game/Game [FD].7z", "Game (Disk A).d88", FD + "a"),
                 disk("Game/Game [FD].7z", "Game (Disk B).d88", FD + "b")]
        sets = disk_sets(disks, ["fd"], FLOPPY_B)
        self.assertEqual(sorted(sets), [FD + "1a", FD + "2a", FD + "a"])
        self.assertEqual(sets[FD + "a"]["setDisks"], [FD + "1a", FD + "2a", FD + "b"])
        self.assertEqual(sets[FD + "2a"], {"setDisks": [FD + "2b"], "media": [{"role": "floppyB", "contentId": FD + "2b"}]})

    def test_single_disk_dump_sets_collapse_too(self):
        disks = [disk("Game/Game [FD] [Set 2].7z", "Game [Set 2].d88", FD + "2"),
                 disk("Game/Game [FD].7z", "Game.d88", FD + "1")]
        self.assertEqual(disk_sets(disks, ["fd"], FLOPPY_B), {FD + "1": {"setDisks": [FD + "2"]}})

    def test_tosec_alternate_and_bad_dumps_hide_behind_the_clean_one(self):
        rom = "sha256-rom-v1:"
        disks = [disk("The Brain (1985)(Hudson)(JP)[b].zip", None, rom + "bad"),
                 disk("The Brain (1985)(Hudson)(JP)[a2].zip", None, rom + "a2"),
                 disk("The Brain (1985)(Hudson)(JP).zip", None, rom + "clean"),
                 disk("The Brain (1985)(Hudson)(JP)[a].zip", None, rom + "a")]
        self.assertEqual(disk_sets(disks, ["rom"], {}), {rom + "clean": {"setDisks": [rom + "a", rom + "a2", rom + "bad"]}})

    def test_a_tosec_alternate_of_one_disk_is_its_own_dump_set(self):
        disks = [disk("Fray (1990)(Micro Cabin)(JP)(Disk 1 of 2)[a].zip", None, FD + "1a"),
                 disk("Fray (1990)(Micro Cabin)(JP)(Disk 1 of 2).zip", None, FD + "1"),
                 disk("Fray (1990)(Micro Cabin)(JP)(Disk 2 of 2).zip", None, FD + "2")]
        self.assertEqual(disk_sets(disks, ["fd"], FLOPPY_B),
                         {FD + "1": {"setDisks": [FD + "1a", FD + "2"], "media": [{"role": "floppyB", "contentId": FD + "2"}]}})

    def test_untagged_disks_go_by_role_and_a_second_program_disk_is_never_inserted(self):
        disks = [disk("Sorcerian/Sorcerian [FD].7z", "Sorcerian (Scenario disk 2).d88", FD + "s2"),
                 disk("Sorcerian/Sorcerian [FD].7z", "Sorcerian (Program disk v1.1).d88", FD + "p11"),
                 disk("Sorcerian/Sorcerian [FD].7z", "Sorcerian (Scenario disk 1).d88", FD + "s1"),
                 disk("Sorcerian/Sorcerian [FD].7z", "Sorcerian (Program disk v1.0).d88", FD + "p10"),
                 disk("Sorcerian/Sorcerian [FD].7z", "Sorcerian (User disk).d88", FD + "u")]
        self.assertEqual(disk_sets(disks, ["fd"], FLOPPY_B)[FD + "p10"]["media"],
                         [{"role": "floppyB", "contentId": FD + "s1"}])

    def test_a_data_disk_is_a_game_disk(self):
        disks = [disk("Ys III/Ys III [FD].7z", "Ys III (Data disk 1).d88", FD + "d1"),
                 disk("Ys III/Ys III [FD].7z", "Ys III (Data disk 3 - Opening).d88", FD + "op"),
                 disk("Ys III/Ys III [FD].7z", "Ys III (Scenario disk).d88", FD + "sc")]
        self.assertEqual(disk_sets(disks, ["fd"], FLOPPY_B)[FD + "op"]["media"],
                         [{"role": "floppyB", "contentId": FD + "sc"}])

    def test_a_games_other_releases_in_its_folder_hide_behind_the_main_one(self):
        disks = [disk("Yaksa (Wolf Team)/Yaksa (demo) [FD].7z", "Yaksa (demo).d88", FD + "demo"),
                 disk("Yaksa (Wolf Team)/Yaksa (hack) [FD].7z", "Yaksa (hack) (Disk 1).d88", FD + "hack1"),
                 disk("Yaksa (Wolf Team)/Yaksa (hack) [FD].7z", "Yaksa (hack) (Disk 2).d88", FD + "hack2"),
                 disk("Yaksa (Wolf Team)/Yaksa (music disk) [FD].7z", "Yaksa (music disk).d88", FD + "music"),
                 disk("Yaksa (Wolf Team)/Yaksa [FD].7z", "Yaksa (Disk 1).d88", FD + "1"),
                 disk("Yaksa (Wolf Team)/Yaksa [FD].7z", "Yaksa (Disk 2).d88", FD + "2")]
        sets = disk_sets(disks, ["fd"], FLOPPY_B)
        self.assertEqual(sets[FD + "1"]["setDisks"], [FD + "2", FD + "demo", FD + "hack1", FD + "music"])
        self.assertEqual(sets[FD + "1"]["media"], [{"role": "floppyB", "contentId": FD + "2"}])
        # The hack keeps its own set, for when the main release is not in the library.
        self.assertEqual(sets[FD + "hack1"]["setDisks"], [FD + "hack2"])

    def test_a_publisher_named_like_a_side_release_is_not_one(self):
        rom = "sha256-rom-v1:"
        disks = [disk("Dam Busters, The (1985)(Sydney Dataproducts).zip", None, rom + "main"),
                 disk("Dam Busters, The (1985)(Comptiq)(Demo).zip", None, rom + "demo")]
        self.assertEqual(disk_sets(disks, ["rom"], {}), {rom + "main": {"setDisks": [rom + "demo"]}})

    def test_releases_in_different_folders_stay_apart(self):
        disks = [disk("100 Yen Disk No. 1/100 Yen Disk No. 1 [FD].7z", "100 Yen Disk No. 1.d88", FD + "1"),
                 disk("100 Yen Disk No. 2/100 Yen Disk No. 2 [FD].7z", "100 Yen Disk No. 2.d88", FD + "2")]
        self.assertEqual(disk_sets(disks, ["fd"], FLOPPY_B), {})

    def test_an_archive_of_only_a_user_or_utility_disk_hides_behind_the_main_release(self):
        disks = [disk("Vain Dream/Vain Dream (access to secret areas) [FD].7z",
                      "Vain Dream (access to secret areas) (User disk).d88", FD + "secret"),
                 disk("Vain Dream/Vain Dream [FD].7z", "Vain Dream (System disk).d88", FD + "system"),
                 disk("Vain Dream/Vain Dream [FD].7z", "Vain Dream (Data disk A).d88", FD + "a")]
        self.assertEqual(disk_sets(disks, ["fd"], FLOPPY_B)[FD + "system"]["setDisks"], [FD + "a", FD + "secret"])

    def test_a_release_of_a_kind_the_app_cannot_start_never_becomes_the_main_one(self):
        tape = "sha256-tape-v1:"
        disks = [disk("Thunder Force/Thunder Force [CT].7z", "Thunder Force {V1 mode}.cmt", tape + "ct"),
                 disk("Thunder Force/Thunder Force (SR) [FD].7z", "Thunder Force (SR).d88", FD + "sr"),
                 disk("Thunder Force/Thunder Force [FD].7z", "Thunder Force {V1 mode}.d88", FD + "v1")]
        self.assertEqual(disk_sets(disks, ["fd"], FLOPPY_B), {FD + "v1": {"setDisks": [FD + "sr", tape + "ct"]}})

    def test_a_utility_disk_is_hidden_but_never_inserted(self):
        disks = [disk("Game/Game [FD].7z", "Game.d88", FD + "a"),
                 disk("Game/Game [FD].7z", "Game (Utility).d88", FD + "u")]
        self.assertEqual(disk_sets(disks, ["fd"], FLOPPY_B), {FD + "a": {"setDisks": [FD + "u"]}})

    def test_a_cd_starts_and_its_user_floppy_goes_beside_it(self):
        disks = [disk("Alshark (1991)(Right Stuff)(Jp)[+UserDisk].zip", "Alshark (User Disk).d88", FD + "user"),
                 disk("Alshark (1991)(Right Stuff)(Jp)[+UserDisk].zip", "Alshark.cue", CD + "cd")]
        self.assertEqual(disk_sets(disks, ["cd", "fd"], {"cd": ("fd", "floppyA", True)}),
                         {CD + "cd": {"setDisks": [FD + "user"], "media": [{"role": "floppyA", "contentId": FD + "user"}]}})

    def test_a_hard_disk_starts_with_its_sets_cd_beside_it(self):
        # KairoTowns#40: a [+HDD] set's CD goes in beside its hard disk, though nothing names it a disk.
        archive = "Strike Commander Plus (1994)(Origin)(Jp-En)[+HDD+CMOS].zip"
        disks = [disk(archive, "Strike Commander Plus (1994)(Origin)(Jp-En).cue", CD + "cd"),
                 disk(archive, "Strike Commander Plus (1994)(Origin)(Jp-En)[100MB HDD].h0", "sha256-hd-v1:hd")]
        companion = {"cd": ("fd", "floppyA", True), "hd": ("cd", "cd", False, True)}
        self.assertEqual(disk_sets(disks, ["hd", "cd", "fd"], companion),
                         {"sha256-hd-v1:hd": {"setDisks": [CD + "cd"], "media": [{"role": "cd", "contentId": CD + "cd"}]}})
        # Without the set-disk flag, an untagged disk beside a disk that does not say it starts is another game's.
        self.assertEqual(disk_sets(disks, ["hd", "cd", "fd"], {"hd": ("cd", "cd", False)}),
                         {"sha256-hd-v1:hd": {"setDisks": [CD + "cd"]}})

    def test_a_disk_that_starts_one_set_is_not_hidden_by_another(self):
        disks = [disk("Game/Game [FD].7z", "Game (Disk A).d88", FD + "a"),
                 disk("Game/Game [FD].7z", "Game (Disk B).d88", FD + "b"),
                 disk("Game/Game [FD] [Set 2].7z", "Game (Disk A).d88", FD + "a"),
                 disk("Game/Game [FD] [Set 2].7z", "Game (Disk B) [b].d88", FD + "b2")]
        self.assertEqual(disk_sets(disks, ["fd"], {}), {FD + "a": {"setDisks": [FD + "b", FD + "b2"]}})

    def test_a_boot_disk_starts_before_the_tagged_disks_and_inserts_the_first(self):
        disks = [disk("Ys II/Ys II [FD].7z", "Ys II (Disk A).d88", FD + "a"),
                 disk("Ys II/Ys II [FD].7z", "Ys II (Disk B).d88", FD + "b"),
                 disk("Ys II/Ys II [FD].7z", "Ys II (Program disk).d88", FD + "p"),
                 disk("Ys II/Ys II [FD].7z", "Ys II (User disk).d88", FD + "u")]
        self.assertEqual(disk_sets(disks, ["fd"], FLOPPY_B),
                         {FD + "p": {"setDisks": [FD + "a", FD + "b", FD + "u"],
                                     "media": [{"role": "floppyB", "contentId": FD + "a"}]}})

    def test_a_damaged_or_altered_copy_never_starts_or_goes_in_drive_b_beside_the_clean_disk(self):
        disks = [disk("Silpheed/Silpheed [FD].7z", "Silpheed (invincibility hack) (Disk A).d88", FD + "hack"),
                 disk("Silpheed/Silpheed [FD].7z", "Silpheed (Disk A) [bad sectors].d88", FD + "bad"),
                 disk("Silpheed/Silpheed [FD].7z", "Silpheed (Disk A).d88", FD + "a"),
                 disk("Silpheed/Silpheed [FD].7z", "Silpheed (Disk B).d88", FD + "b")]
        self.assertEqual(disk_sets(disks, ["fd"], FLOPPY_B)[FD + "a"]["media"],
                         [{"role": "floppyB", "contentId": FD + "b"}])

    def test_a_damaged_disk_the_set_has_no_clean_copy_of_still_goes_in_drive_b(self):
        disks = [disk("Star Trap/Star Trap [FD].7z", "Star Trap (Disk 1).d88", FD + "1"),
                 disk("Star Trap/Star Trap [FD].7z", "Star Trap (Disk 2) [bad sectors].d88", FD + "2")]
        self.assertEqual(disk_sets(disks, ["fd"], FLOPPY_B)[FD + "1"]["media"],
                         [{"role": "floppyB", "contentId": FD + "2"}])

    def test_another_volume_version_or_demo_in_the_archive_never_goes_in_drive_b(self):
        def media(*names):
            disks = [disk("Set/Set [FD].7z", n, FD + str(i)) for i, n in enumerate(names)]
            return disk_sets(disks, ["fd"], FLOPPY_B).get(FD + "0", {}).get("media")
        self.assertIsNone(media("Music Disk Vol. 09 (Disk 1).d88", "Music Disk Vol. 08 (Disk 2).d88"))
        self.assertIsNone(media("Lipstick (#1 - Lolita).d88", "Lipstick (#2 - Joshikousei).d88"))
        self.assertIsNone(media("Dinosaur (Program disk).d88", "Dinosaur (Demo disk).d88"))
        # Beside a boot disk, an untagged disk is the game it boots.
        self.assertEqual(media("Ougon no Haka (Boot disk).d88", "Ougon no Haka.d88"),
                         [{"role": "floppyB", "contentId": FD + "1"}])
        # A tagged demo is one of the set's disks.
        self.assertEqual(media("Xak (Disk 1 - Demo disk A).d88", "Xak (Disk 2 - Demo disk B).d88"),
                         [{"role": "floppyB", "contentId": FD + "1"}])

    def test_a_tagged_disk_named_as_the_boot_disk_starts_the_set(self):
        # Kairo64#6: Elvira II starts from its third disk, the one named (Boot).
        disks = [disk(f"C64/Elvira II (Europe) ({tag}).D64", None, "sha256-disk-v1:" + key)
                 for tag, key in (("Disk 1 Side A", "1a"), ("Disk 1 Side B", "1b"), ("Disk 3) (Boot", "3"))]
        self.assertEqual(list(disk_sets(disks, ["disk"], {})), ["sha256-disk-v1:3"])

    def test_a_disk_kept_only_as_alternate_dumps_starts_from_its_first_one(self):
        disks = [disk("Prajator/Prajator [FD].7z", "Prajator (Disk A).d88", FD + "a"),
                 disk("Prajator/Prajator [FD].7z", "Prajator (Disk B).d88", FD + "b"),
                 disk("Prajator/Prajator [FD].7z", "alts/Prajator (System disk) [Alt 2].d88", FD + "s2"),
                 disk("Prajator/Prajator [FD].7z", "alts/Prajator (System disk) [Alt 1].d88", FD + "s1")]
        self.assertEqual(disk_sets(disks, ["fd"], FLOPPY_B),
                         {FD + "s1": {"setDisks": [FD + "a", FD + "b", FD + "s2"],
                                      "media": [{"role": "floppyB", "contentId": FD + "a"}]}})

    def test_a_single_disk_is_not_a_set(self):
        self.assertEqual(disk_sets([disk("Game.zip", "Game.d88", FD + "a")], ["fd"], FLOPPY_B), {})



class UngroupedSetsTest(unittest.TestCase):
    def test_a_set_whose_disks_all_show_is_ungrouped(self):
        archives = [{"path": "Game (Side A).d64", "disks": [{"contentId": FD + "a"}]},
                    {"path": "Game (Side B).d64", "disks": [{"contentId": FD + "b"}]}]
        self.assertEqual(ungrouped_sets(archives, {}), [("Game", ["Game (Side A).d64", "Game (Side B).d64"])])

    def test_byte_identical_copies_of_one_disk_are_not_a_set(self):
        archives = [{"path": "Game (Side A).d64", "disks": [{"contentId": FD + "a"}]},
                    {"path": "Game (Side B).d64", "disks": [{"contentId": FD + "a"}]}]
        self.assertEqual(ungrouped_sets(archives, {}), [])


if __name__ == "__main__":
    unittest.main()
