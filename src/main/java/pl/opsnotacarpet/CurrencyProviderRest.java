package pl.opsnotacarpet;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class CurrencyProviderRest {

    private double eurUsdRate = 1.0;

    @Value("${spread}")
    private double spread = 0.0;

    @Value("${wizardLoops}")
    private long wizaardLoops = 100000000;

    @RequestMapping(path = "/rate", method = RequestMethod.GET)
    public String rate(@RequestParam String source, @RequestParam String destination) {
        return String.valueOf(spread("EUR".equals(source) && "USD".equals(destination) ? eurUsdRate : rateWizard()));
    }

    private double rateWizard() {
        double rate = 0;
        for (long i=0l; i<wizaardLoops; i++) {
            rate = Math.tan(Math.atan(Math.PI * Math.E));
        }
        return rate*(1.0 + spread);
    }

    private double spread(double rate) {
        return rate*(1.0 + spread);
    }

}
