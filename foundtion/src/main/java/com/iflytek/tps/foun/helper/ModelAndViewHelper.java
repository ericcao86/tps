package com.iflytek.tps.foun.helper;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.view.RedirectView;
import org.springframework.web.servlet.view.UrlBasedViewResolver;

/**
 * Created by losyn on 5/5/16.
 */
public class ModelAndViewHelper {
    /**
     * 判断是否为 spring ModelAndView
     */
    public static boolean isRedirectView(ModelAndView mav) {
        return mav != null
                && mav.getModel() != null
                && !(mav.getView() instanceof RedirectView
                || (mav.getViewName() != null
                && mav.getViewName().startsWith(UrlBasedViewResolver.REDIRECT_URL_PREFIX)));
    }
}
