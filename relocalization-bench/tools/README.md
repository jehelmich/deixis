# Feature evaluation tools

Research/eval scripts kept out of the Gradle build (they need PyTorch). `viewpoint_bench.py`
produced the ORB-vs-XFeat viewpoint numbers in [`../../docs/relocalization-testing.md`](../../docs/relocalization-testing.md).

```sh
python3 -m venv venv && . venv/bin/activate
pip install torch kornia opencv-python-headless numpy tqdm
git clone https://github.com/verlab/accelerated_features        # XFeat + weights, Apache-2.0
python viewpoint_bench.py --image ../src/test/resources/room.png --xfeat-repo accelerated_features
```

The in-build, no-dependency ORB viewpoint benchmark also lives as a JUnit test
(`ViewpointRobustnessTest`) so the ORB baseline runs in CI without any of this.
