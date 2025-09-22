// ui/src/Switcher.tsx
import React, {useEffect, useState} from "react";
import ManagerDashboard from "./ManagerDashboard";
import PropertyDetails from "./PropertyDetails";
import "./styles.css";

function useQuery() {
    const [sp, setSp] = useState(() => new URLSearchParams(window.location.search));
    useEffect(() => {
        const onPop = () => setSp(new URLSearchParams(window.location.search));
        window.addEventListener("popstate", onPop);
        return () => window.removeEventListener("popstate", onPop);
    }, []);
    return sp;
}

export default function Switcher() {
    const sp = useQuery();
    const view = sp.get("view");
    const listing = sp.get("listing") ?? "";
    const reviewId = sp.get("reviewId") ?? "";

    if (view === "property" && listing) {
        return <PropertyDetails listing={listing} reviewId={reviewId}/>;
    }
    return <ManagerDashboard/>;
}

// navigation helper
export function goTo(view: string, params: Record<string, string>) {
    const sp = new URLSearchParams({view, ...params});
    const next = `${window.location.pathname}?${sp.toString()}`;
    window.history.pushState({}, "", next);
    window.dispatchEvent(new PopStateEvent("popstate"));
}
