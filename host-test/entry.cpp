// The shared boot test's executable (Kairo#115, Kairo#192): on the PC the whole test is in it; on
// the device the test is in the app's library (KAIRO_DEVICE_TEST), built as the app's library is, so
// what it measures is the code the app runs.
extern "C" int kairo_host_test_main(int argc, char **argv);

int main(int argc, char **argv) { return kairo_host_test_main(argc, argv); }
