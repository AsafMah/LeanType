package helium314.keyboard.latin;

public class LatinIME {
    public void onCreate() {
        helium314.keyboard.latin.gesture.SwipeGestureEngine.initialize(this);
    }

    public void onDestroy() {
        helium314.keyboard.latin.gesture.SwipeGestureEngine.cancelIndexing();
    }
}
