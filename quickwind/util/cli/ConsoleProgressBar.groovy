import quickwind.util.cli

class ConsoleProgressBar {
    String title = "Processing"
    int interval = 200
    String format = "%s: [%s]  %s %3d%%"
    int wedth = 100
    Closure percentageTeller
    private Thread thread
    List rollingBar = ['-', '\\', '|', '/']
    private rollingBarCount = 0
    int percentage = 0
    
    void start() {   
        if(percentageTeller) {
            thread = Thread.start {                
                while(printProgress()) {                    
                    sleep(150) {true}
                }
            }
        }
    }
    
    private boolean printProgress() {
        def tmp = percentageTeller.call()       
        if(tmp > 0) {
            percentage = tmp > 100 ? 100:tmp
        }
        printf "\r${format}", title, ('='* ((percentage*wedth)/100) ).padRight(wedth),rollingBar[rollingBarCount%rollingBar.size()],percentage
        if(percentage == 100) {
            printf "\r${format}", title, 100, ' ', '='* 100 
            return false
        }
        rollingBarCount++
        return true
    }
    
    void stop() {
        if(thread) thread.stop()
    }
    
    void done() {
        stop()
        printf "\r${format}", title, 100, ' ', '='* 100 
    }
    
    void join() {
        if(thread) thread.join()
    }
}

/* Test code:
def percentage = 0

ConsoleProgressBar bar = new ConsoleProgressBar(title: "Doing something", percentageTeller:{-> percentage})

def t1 = Thread.start {
    bar.start()
    while (percentage <100) {
        percentage++;
        sleep(50)
    }
} 

sleep 1000
t1.join()
bar.done()

return*/

